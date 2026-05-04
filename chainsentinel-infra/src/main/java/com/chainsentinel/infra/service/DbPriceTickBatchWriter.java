package com.chainsentinel.infra.service;

import com.chainsentinel.infra.config.PriceTickIngestProperties;
import com.chainsentinel.infra.entity.PriceTickEntity;
import com.chainsentinel.infra.repository.PriceTickRepository;
import com.chainsentinel.price.stream.PriceStreamQuote;
import com.chainsentinel.price.stream.PriceTickBatchWriter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DbPriceTickBatchWriter implements PriceTickBatchWriter {

	private static final Logger log = LoggerFactory.getLogger(DbPriceTickBatchWriter.class);
	private static final String METRIC_WS_TICK_BATCH_TOTAL = "price_ws_tick_batch_total";
	private static final String METRIC_WS_TICK_QUEUE_SIZE = "price_ws_tick_queue_size";
	private static final String METRIC_WS_TICK_OVERFLOW_SIZE = "price_ws_tick_overflow_size";
	private static final String METRIC_WS_TICK_FILL_RATIO = "price_ws_tick_fill_ratio";
	private static final String METRIC_WS_TICK_DROPPED_TOTAL = "price_ws_tick_dropped_total";
	private static final String METRIC_WS_TICK_OVERFLOW_TOTAL = "price_ws_tick_overflow_total";
	private static final String METRIC_WS_TICK_FLUSH_DURATION = "price_ws_tick_flush_duration";
	private static final String METRIC_WS_TICK_BUFFER_DELAY = "price_ws_tick_buffer_delay";
	private static final String METRIC_WS_TICK_SUPPRESSED_TOTAL = "price_ws_tick_suppressed_total";

	private final PriceTickRepository priceTickRepository;
	private final PriceTickIngestProperties properties;
	private final MeterRegistry meterRegistry;
	private final ConcurrentLinkedQueue<PendingQuote> queue = new ConcurrentLinkedQueue<>();
	private final ConcurrentHashMap<String, PendingQuote> overflowLatestByInst = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, PriceStreamQuote> latestAcceptedQuoteByInst = new ConcurrentHashMap<>();
	private final AtomicInteger queueSize = new AtomicInteger(0);
	private final AtomicInteger overflowSize = new AtomicInteger(0);
	private final AtomicBoolean flushing = new AtomicBoolean(false);
	private final AtomicLong lastDropLogAt = new AtomicLong(0L);

	public DbPriceTickBatchWriter(
		PriceTickRepository priceTickRepository,
		PriceTickIngestProperties properties,
		MeterRegistry meterRegistry
	) {
		this.priceTickRepository = priceTickRepository;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
		meterRegistry.gauge(METRIC_WS_TICK_QUEUE_SIZE, queueSize);
		meterRegistry.gauge(METRIC_WS_TICK_OVERFLOW_SIZE, overflowSize);
		meterRegistry.gauge(METRIC_WS_TICK_FILL_RATIO, this, DbPriceTickBatchWriter::queueFillRatio);
	}

	@Override
	public void enqueue(PriceStreamQuote quote) {
		if (!properties.isEnabled() || quote == null) {
			return;
		}
		if (shouldSuppressQuote(quote)) {
			return;
		}
		PendingQuote pendingQuote = new PendingQuote(quote, System.currentTimeMillis());
		int current = queueSize.get();
		if (current >= properties.getQueueCapacity()) {
			mergeLatestWhenQueueFull(pendingQuote, current);
			triggerFlushIfNeeded();
			return;
		}
		queue.offer(pendingQuote);
		queueSize.incrementAndGet();
		latestAcceptedQuoteByInst.put(overflowKey(quote), quote);
		triggerFlushIfNeeded();
	}

	public TickIngestStatus currentStatus() {
		return new TickIngestStatus(
			properties.isEnabled(),
			properties.getBatchSize(),
			properties.getQueueCapacity(),
			properties.getFlushIntervalMs(),
			effectiveHighWatermark(),
			properties.getMinPersistChangeRatio(),
			queueFillRatio(),
			bufferedSize(),
			flushing.get()
		);
	}

	@Scheduled(fixedDelayString = "${chainsentinel.price.tick.flush-interval-ms:1000}")
	public void flushBySchedule() {
		flushNow();
	}

	@PreDestroy
	public void flushOnShutdown() {
		flushNow();
	}

	@Transactional
	public void flushNow() {
		if (!properties.isEnabled()) {
			return;
		}
		if (!flushing.compareAndSet(false, true)) {
			return;
		}
		try {
			int batchSize = Math.max(1, properties.getBatchSize());
			while (true) {
				long flushStartedAt = System.nanoTime();
				List<PendingQuote> drained = drainBatch(batchSize);
				if (drained.isEmpty()) {
					return;
				}
				flushBatch(drained);
				meterRegistry.timer(METRIC_WS_TICK_FLUSH_DURATION).record(System.nanoTime() - flushStartedAt, java.util.concurrent.TimeUnit.NANOSECONDS);
			}
		} finally {
			flushing.set(false);
		}
	}

	private void flushBatch(List<PendingQuote> drained) {
		List<PriceTickEntity> entities = dedupAndConvert(drained);
		if (entities.isEmpty()) {
			return;
		}
		recordBufferDelay(drained);
		try {
			priceTickRepository.saveAll(entities);
			meterRegistry.counter(METRIC_WS_TICK_BATCH_TOTAL, "status", "success").increment();
		} catch (DataIntegrityViolationException ex) {
			saveIndividuallyIgnoreDuplicate(entities);
			meterRegistry.counter(METRIC_WS_TICK_BATCH_TOTAL, "status", "partial_duplicate").increment();
		}
	}

	private List<PendingQuote> drainBatch(int batchSize) {
		int sizeLimit = Math.max(1, batchSize);
		List<PendingQuote> list = new ArrayList<>(sizeLimit);
		for (int i = 0; i < sizeLimit; i++) {
			PendingQuote pendingQuote = queue.poll();
			if (pendingQuote == null) {
				break;
			}
			queueSize.decrementAndGet();
			list.add(pendingQuote);
		}
		if (list.size() >= sizeLimit) {
			return list;
		}
		Iterator<Map.Entry<String, PendingQuote>> iterator = overflowLatestByInst.entrySet().iterator();
		while (iterator.hasNext() && list.size() < sizeLimit) {
			Map.Entry<String, PendingQuote> entry = iterator.next();
			PendingQuote pendingQuote = entry.getValue();
			if (pendingQuote == null) {
				overflowLatestByInst.remove(entry.getKey());
				continue;
			}
			if (overflowLatestByInst.remove(entry.getKey(), pendingQuote)) {
				overflowSize.decrementAndGet();
				queueSize.decrementAndGet();
				list.add(pendingQuote);
			}
		}
		return list;
	}

	private List<PriceTickEntity> dedupAndConvert(List<PendingQuote> drained) {
		Map<String, PriceTickEntity> unique = new LinkedHashMap<>(drained.size());
		for (PendingQuote pendingQuote : drained) {
			PriceStreamQuote quote = pendingQuote.quote();
			if (quote == null || quote.instId() == null || quote.instId().isBlank() || quote.price() == null || quote.ts() <= 0) {
				continue;
			}
			String key = quote.providerName() + "|" + quote.instId() + "|" + quote.ts();
			PriceTickEntity entity = new PriceTickEntity();
			entity.setProviderName(quote.providerName());
			entity.setInstType(quote.instType() == null ? "UNKNOWN" : quote.instType().name());
			entity.setInstId(quote.instId());
			entity.setBaseSymbol(resolveBaseSymbol(quote));
			entity.setQuoteSymbol(resolveQuoteSymbol(quote));
			entity.setPrice(quote.price());
			entity.setQuoteTs(quote.ts());
			unique.putIfAbsent(key, entity);
		}
		return new ArrayList<>(unique.values());
	}

	private void saveIndividuallyIgnoreDuplicate(List<PriceTickEntity> entities) {
		for (PriceTickEntity entity : entities) {
			try {
				priceTickRepository.save(entity);
			} catch (DataIntegrityViolationException duplicate) {
				meterRegistry.counter(METRIC_WS_TICK_BATCH_TOTAL, "status", "duplicate").increment();
			}
		}
	}

	private void mergeLatestWhenQueueFull(PendingQuote pendingQuote, int currentSize) {
		String key = overflowKey(pendingQuote.quote());
		if (key == null) {
			meterRegistry.counter(METRIC_WS_TICK_DROPPED_TOTAL, "reason", "queue_full_invalid_key").increment();
			logQueueDropIfNeeded(currentSize);
			return;
		}
		PendingQuote previous = overflowLatestByInst.put(key, pendingQuote);
		latestAcceptedQuoteByInst.put(key, pendingQuote.quote());
		if (previous == null) {
			overflowSize.incrementAndGet();
			queueSize.incrementAndGet();
			meterRegistry.counter(METRIC_WS_TICK_OVERFLOW_TOTAL, "action", "buffered_latest").increment();
		} else {
			meterRegistry.counter(METRIC_WS_TICK_OVERFLOW_TOTAL, "action", "replaced_latest").increment();
		}
		logQueueDropIfNeeded(currentSize);
	}

	private void logQueueDropIfNeeded(int currentSize) {
		long now = System.currentTimeMillis();
		long last = lastDropLogAt.get();
		if (last > 0 && now - last < 10000L) {
			return;
		}
		lastDropLogAt.set(now);
		log.warn("price.ws.tick.backpressure reason=queue_full queueSize={} overflowSize={} capacity={}",
			currentSize, overflowSize.get(), properties.getQueueCapacity());
	}

	private void recordBufferDelay(List<PendingQuote> drained) {
		long now = System.currentTimeMillis();
		for (PendingQuote pendingQuote : drained) {
			long delayMs = Math.max(0L, now - pendingQuote.enqueuedAtMs());
			meterRegistry.timer(METRIC_WS_TICK_BUFFER_DELAY).record(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
		}
	}

	private String overflowKey(PriceStreamQuote quote) {
		if (quote == null || quote.providerName() == null || quote.instId() == null) {
			return null;
		}
		return quote.providerName() + "|" + quote.instId();
	}

	private double queueFillRatio() {
		int capacity = Math.max(1, properties.getQueueCapacity());
		return Math.min(1D, (double) bufferedSize() / (double) capacity);
	}

	private void triggerFlushIfNeeded() {
		if (flushing.get()) {
			return;
		}
		if (bufferedSize() < effectiveHighWatermark()) {
			return;
		}
		flushNow();
	}

	private boolean shouldSuppressQuote(PriceStreamQuote quote) {
		String key = overflowKey(quote);
		if (key == null || quote.price() == null) {
			return false;
		}
		PriceStreamQuote previous = latestAcceptedQuoteByInst.get(key);
		if (previous == null || previous.price() == null) {
			return false;
		}
		if (previous.price().compareTo(quote.price()) == 0) {
			meterRegistry.counter(METRIC_WS_TICK_SUPPRESSED_TOTAL, "reason", "unchanged_price").increment();
			return true;
		}
		double minPersistChangeRatio = Math.max(0D, properties.getMinPersistChangeRatio());
		if (minPersistChangeRatio <= 0D) {
			return false;
		}
		BigDecimal baseline = previous.price().abs();
		if (baseline.compareTo(BigDecimal.ZERO) == 0) {
			return false;
		}
		BigDecimal diffRatio = quote.price()
			.subtract(previous.price())
			.abs()
			.divide(baseline, 12, RoundingMode.HALF_UP);
		if (diffRatio.compareTo(BigDecimal.valueOf(minPersistChangeRatio)) <= 0) {
			meterRegistry.counter(METRIC_WS_TICK_SUPPRESSED_TOTAL, "reason", "tiny_change").increment();
			return true;
		}
		return false;
	}

	private int effectiveHighWatermark() {
		int configured = properties.getHighWatermark();
		if (configured > 0) {
			return configured;
		}
		return Math.max(1, properties.getBatchSize());
	}

	private int bufferedSize() {
		return Math.max(0, queueSize.get());
	}

	private String resolveBaseSymbol(PriceStreamQuote quote) {
		if (quote.baseSymbol() != null && !quote.baseSymbol().isBlank()) {
			return quote.baseSymbol();
		}
		String[] parts = splitInstId(quote.instId());
		return parts[0];
	}

	private String resolveQuoteSymbol(PriceStreamQuote quote) {
		if (quote.quoteSymbol() != null && !quote.quoteSymbol().isBlank()) {
			return quote.quoteSymbol();
		}
		String[] parts = splitInstId(quote.instId());
		return parts[1];
	}

	private String[] splitInstId(String instId) {
		if (instId == null || instId.isBlank()) {
			return new String[] {"UNKNOWN", "UNKNOWN"};
		}
		String[] parts = instId.split("-");
		if (parts.length < 2) {
			return new String[] {instId, "UNKNOWN"};
		}
		return new String[] {parts[0], parts[1]};
	}

	public record TickIngestStatus(
		boolean enabled,
		int batchSize,
		int queueCapacity,
		long flushIntervalMs,
		int highWatermark,
		double minPersistChangeRatio,
		double queueFillRatio,
		int queueSize,
		boolean flushing
	) {
	}

	private record PendingQuote(
		PriceStreamQuote quote,
		long enqueuedAtMs
	) {
	}
}
