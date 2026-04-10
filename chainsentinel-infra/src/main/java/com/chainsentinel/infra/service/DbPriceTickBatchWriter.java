package com.chainsentinel.infra.service;

import com.chainsentinel.infra.config.PriceTickIngestProperties;
import com.chainsentinel.infra.entity.PriceTickEntity;
import com.chainsentinel.infra.repository.PriceTickRepository;
import com.chainsentinel.price.stream.PriceStreamQuote;
import com.chainsentinel.price.stream.PriceTickBatchWriter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	private static final String METRIC_WS_TICK_DROPPED_TOTAL = "price_ws_tick_dropped_total";

	private final PriceTickRepository priceTickRepository;
	private final PriceTickIngestProperties properties;
	private final MeterRegistry meterRegistry;
	private final ConcurrentLinkedQueue<PriceStreamQuote> queue = new ConcurrentLinkedQueue<>();
	private final AtomicInteger queueSize = new AtomicInteger(0);
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
	}

	@Override
	public void enqueue(PriceStreamQuote quote) {
		if (!properties.isEnabled() || quote == null) {
			return;
		}
		int current = queueSize.get();
		if (current >= properties.getQueueCapacity()) {
			meterRegistry.counter(METRIC_WS_TICK_DROPPED_TOTAL, "reason", "queue_full").increment();
			logQueueDropIfNeeded(current);
			return;
		}
		queue.offer(quote);
		queueSize.incrementAndGet();
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
			List<PriceStreamQuote> drained = drainBatch(properties.getBatchSize());
			if (drained.isEmpty()) {
				return;
			}
			List<PriceTickEntity> entities = dedupAndConvert(drained);
			if (entities.isEmpty()) {
				return;
			}
			try {
				priceTickRepository.saveAll(entities);
				meterRegistry.counter(METRIC_WS_TICK_BATCH_TOTAL, "status", "success").increment();
			} catch (DataIntegrityViolationException ex) {
				saveIndividuallyIgnoreDuplicate(entities);
				meterRegistry.counter(METRIC_WS_TICK_BATCH_TOTAL, "status", "partial_duplicate").increment();
			}
		} finally {
			flushing.set(false);
		}
	}

	private List<PriceStreamQuote> drainBatch(int batchSize) {
		int sizeLimit = Math.max(1, batchSize);
		List<PriceStreamQuote> list = new ArrayList<>(sizeLimit);
		for (int i = 0; i < sizeLimit; i++) {
			PriceStreamQuote quote = queue.poll();
			if (quote == null) {
				break;
			}
			queueSize.decrementAndGet();
			list.add(quote);
		}
		return list;
	}

	private List<PriceTickEntity> dedupAndConvert(List<PriceStreamQuote> drained) {
		Map<String, PriceTickEntity> unique = new LinkedHashMap<>();
		for (PriceStreamQuote quote : drained) {
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

	private void logQueueDropIfNeeded(int currentSize) {
		long now = System.currentTimeMillis();
		long last = lastDropLogAt.get();
		if (last > 0 && now - last < 10000L) {
			return;
		}
		lastDropLogAt.set(now);
		log.warn("price.ws.tick.drop reason=queue_full queueSize={} capacity={}", currentSize, properties.getQueueCapacity());
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
}
