package com.chainsentinel.infra.job;

import com.chainsentinel.infra.config.PriceTickRetentionProperties;
import com.chainsentinel.infra.repository.PriceTickRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PriceTickRetentionJob {

	private static final Logger log = LoggerFactory.getLogger(PriceTickRetentionJob.class);
	private static final String METRIC_TICK_RETENTION_TOTAL = "price_tick_retention_total";
	private static final String METRIC_JOB_LAST_SUCCESS_TIMESTAMP = "chainsentinel_job_last_success_timestamp_seconds";
	private static final String JOB_TAG = "price_tick_retention";

	private final PriceTickRepository priceTickRepository;
	private final PriceTickRetentionProperties retentionProperties;
	private final MeterRegistry meterRegistry;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicLong lastSuccessEpochSeconds = new AtomicLong(0L);

	public PriceTickRetentionJob(
		PriceTickRepository priceTickRepository,
		PriceTickRetentionProperties retentionProperties,
		MeterRegistry meterRegistry
	) {
		this.priceTickRepository = priceTickRepository;
		this.retentionProperties = retentionProperties;
		this.meterRegistry = meterRegistry;
		Gauge.builder(METRIC_JOB_LAST_SUCCESS_TIMESTAMP, lastSuccessEpochSeconds, AtomicLong::get)
			.tag("job", JOB_TAG)
			.register(meterRegistry);
	}

	@Scheduled(fixedDelayString = "${chainsentinel.price.tick-retention.cleanup-interval-ms:3600000}")
	@Transactional
	public void run() {
		if (!retentionProperties.isEnabled()) {
			return;
		}
		if (!running.compareAndSet(false, true)) {
			log.warn("price.tick.retention.skip previous run still in progress");
			return;
		}
		try {
			int retentionDays = Math.max(1, retentionProperties.getRetentionDays());
			long cutoffTs = Instant.now().minus(retentionDays, ChronoUnit.DAYS).toEpochMilli();
			int deleted = priceTickRepository.deleteByQuoteTsBefore(cutoffTs);
			log.info("price.tick.retention.done retentionDays={} cutoffTs={} deleted={}", retentionDays, cutoffTs, deleted);
			meterRegistry.counter(METRIC_TICK_RETENTION_TOTAL, "status", "success").increment();
			lastSuccessEpochSeconds.set(Instant.now().getEpochSecond());
		} catch (Exception ex) {
			log.error("price.tick.retention.failed", ex);
			meterRegistry.counter(METRIC_TICK_RETENTION_TOTAL, "status", "failed").increment();
		} finally {
			running.set(false);
		}
	}
}
