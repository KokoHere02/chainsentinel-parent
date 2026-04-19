package com.chainsentinel.infra.service;

import com.chainsentinel.infra.config.PriceTickBackfillProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PriceTickBackfillDispatchService {

	private static final Logger log = LoggerFactory.getLogger(PriceTickBackfillDispatchService.class);
	private static final int DEFAULT_RETENTION_DAYS = 30;
	private static final String DEFAULT_BAR = "1m";
	private static final int DEFAULT_PAGE_LIMIT = 300;
	private static final int DEFAULT_MAX_ROUNDS = 1000;
	private static final long DEFAULT_SLEEP_MS = 50L;
	private static final int DEFAULT_GLOBAL_MAX_CONCURRENT = 1;
	private static final String METRIC_BACKFILL_DISPATCH_TOTAL = "price_tick_backfill_dispatch_total";
	private static final String METRIC_BACKFILL_DURATION = "price_tick_backfill_duration";
	private static final String METRIC_BACKFILL_FAILURE_TOTAL = "price_tick_backfill_failure_total";

	private final OkxPriceTickBackfillService okxPriceTickBackfillService;
	private final Executor backfillExecutor;
	private final MeterRegistry meterRegistry;
	private final PriceTickBackfillProperties backfillProperties;
	private final Set<String> pendingInstIds = ConcurrentHashMap.newKeySet();
	private final Semaphore runningPermits;
	private final int globalMaxConcurrent;

	public PriceTickBackfillDispatchService(
		OkxPriceTickBackfillService okxPriceTickBackfillService,
		@Qualifier("priceTickBackfillExecutor") Executor backfillExecutor,
		MeterRegistry meterRegistry,
		PriceTickBackfillProperties backfillProperties
	) {
		this.okxPriceTickBackfillService = okxPriceTickBackfillService;
		this.backfillExecutor = backfillExecutor;
		this.meterRegistry = meterRegistry;
		this.backfillProperties = backfillProperties;
		this.globalMaxConcurrent = resolveGlobalMaxConcurrent(backfillProperties);
		this.runningPermits = new Semaphore(this.globalMaxConcurrent, true);
		log.info("price.tick.backfill.dispatch.init globalMaxConcurrent={}", this.globalMaxConcurrent);
	}

	public void submitLast30Days(String instId, String trigger) {
		String normalizedInstId = normalizeInstId(instId);
		String normalizedTrigger = normalizeTrigger(trigger);
		if (normalizedInstId == null) {
			incrementDispatchCounter(normalizedTrigger, "skipped_invalid_inst");
			return;
		}
		if (!pendingInstIds.add(normalizedInstId)) {
			log.info("price.tick.backfill.skip reason=pending instId={} trigger={}", normalizedInstId, normalizedTrigger);
			incrementDispatchCounter(normalizedTrigger, "skipped_pending");
			return;
		}
		try {
			incrementDispatchCounter(normalizedTrigger, "submitted");
			backfillExecutor.execute(() -> doBackfill(normalizedInstId, normalizedTrigger));
		} catch (Exception ex) {
			String reason = classifyFailureReason(ex);
			pendingInstIds.remove(normalizedInstId);
			incrementDispatchCounter(normalizedTrigger, "submit_failed");
			recordFailure(normalizedTrigger, "submit", reason);
			log.warn("price.tick.backfill.submit.failed instId={} trigger={} reason={} error={}",
				normalizedInstId,
				normalizedTrigger,
				reason,
				ex.getMessage());
		}
	}

	private void doBackfill(String instId, String trigger) {
		long startedMs = System.currentTimeMillis();
		BackfillParams params = resolveBackfillParams();
		boolean acquired = false;
		try {
			acquirePermit(instId, trigger);
			acquired = true;
			long toTs = startedMs;
			long fromTs = Instant.ofEpochMilli(toTs)
				.minus(params.retentionDays(), ChronoUnit.DAYS)
				.toEpochMilli();
			log.info(
				"price.tick.backfill.enqueue instId={} trigger={} from={} to={} days={} bar={} pageLimit={} maxRounds={} sleepMs={} inFlightLimit={}",
				instId,
				trigger,
				fromTs,
				toTs,
				params.retentionDays(),
				params.bar(),
				params.pageLimit(),
				params.maxRounds(),
				params.sleepMs(),
				globalMaxConcurrent
			);
			okxPriceTickBackfillService.backfill(
				instId,
				fromTs,
				toTs,
				params.bar(),
				params.pageLimit(),
				params.maxRounds(),
				params.sleepMs()
			);
			incrementDispatchCounter(trigger, "success");
			recordBackfillDuration(trigger, "success", startedMs);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			incrementDispatchCounter(trigger, "acquire_interrupted");
			recordFailure(trigger, "acquire", "interrupted");
			recordBackfillDuration(trigger, "failed", startedMs);
			log.warn("price.tick.backfill.acquire.interrupted instId={} trigger={}", instId, trigger);
		} catch (Exception ex) {
			String reason = classifyFailureReason(ex);
			incrementDispatchCounter(trigger, "failed");
			recordFailure(trigger, "run", reason);
			recordBackfillDuration(trigger, "failed", startedMs);
			log.warn("price.tick.backfill.async.failed instId={} trigger={} reason={} error={}",
				instId,
				trigger,
				reason,
				ex.getMessage());
		} finally {
			if (acquired) {
				runningPermits.release();
			}
			pendingInstIds.remove(instId);
		}
	}

	private void acquirePermit(String instId, String trigger) throws InterruptedException {
		if (runningPermits.availablePermits() == 0) {
			log.info("price.tick.backfill.wait_global_limit instId={} trigger={} limit={}", instId, trigger, globalMaxConcurrent);
		}
		runningPermits.acquire();
	}

	private String classifyFailureReason(Throwable ex) {
		if (ex == null) {
			return "unknown";
		}
		if (ex instanceof IllegalArgumentException) {
			return "invalid_param";
		}
		if (ex instanceof IllegalStateException && messageContains(ex, "already running")) {
			return "inst_running";
		}
		if (ex instanceof HttpTimeoutException || ex instanceof SocketTimeoutException) {
			return "upstream_timeout";
		}
		if (messageContains(ex, "timed out") || messageContains(ex, "timeout")) {
			return "upstream_timeout";
		}
		return "other";
	}

	private boolean messageContains(Throwable ex, String token) {
		String message = ex.getMessage();
		if (!StringUtils.hasText(message)) {
			return false;
		}
		return message.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
	}

	private BackfillParams resolveBackfillParams() {
		int retentionDays = backfillProperties.getRetentionDays() > 0
			? backfillProperties.getRetentionDays()
			: DEFAULT_RETENTION_DAYS;
		String bar = StringUtils.hasText(backfillProperties.getBar())
			? backfillProperties.getBar().trim()
			: DEFAULT_BAR;
		int pageLimit = backfillProperties.getPageLimit() > 0
			? backfillProperties.getPageLimit()
			: DEFAULT_PAGE_LIMIT;
		int maxRounds = backfillProperties.getMaxRounds() > 0
			? backfillProperties.getMaxRounds()
			: DEFAULT_MAX_ROUNDS;
		long sleepMs = backfillProperties.getSleepMs() >= 0L
			? backfillProperties.getSleepMs()
			: DEFAULT_SLEEP_MS;
		return new BackfillParams(retentionDays, bar, pageLimit, maxRounds, sleepMs);
	}

	private int resolveGlobalMaxConcurrent(PriceTickBackfillProperties properties) {
		return properties.getGlobalMaxConcurrent() > 0
			? properties.getGlobalMaxConcurrent()
			: DEFAULT_GLOBAL_MAX_CONCURRENT;
	}

	private void incrementDispatchCounter(String trigger, String status) {
		meterRegistry.counter(METRIC_BACKFILL_DISPATCH_TOTAL, "trigger", trigger, "status", status).increment();
	}

	private void recordFailure(String trigger, String phase, String reason) {
		meterRegistry.counter(METRIC_BACKFILL_FAILURE_TOTAL, "trigger", trigger, "phase", phase, "reason", reason).increment();
	}

	private void recordBackfillDuration(String trigger, String status, long startedMs) {
		long durationMs = Math.max(0L, System.currentTimeMillis() - startedMs);
		meterRegistry.timer(METRIC_BACKFILL_DURATION, "trigger", trigger, "status", status)
			.record(durationMs, TimeUnit.MILLISECONDS);
	}

	private String normalizeInstId(String instId) {
		if (!StringUtils.hasText(instId)) {
			return null;
		}
		return instId.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeTrigger(String trigger) {
		if (!StringUtils.hasText(trigger)) {
			return "unknown";
		}
		return trigger.trim().toLowerCase(Locale.ROOT);
	}

	private record BackfillParams(
		int retentionDays,
		String bar,
		int pageLimit,
		int maxRounds,
		long sleepMs
	) {
	}
}