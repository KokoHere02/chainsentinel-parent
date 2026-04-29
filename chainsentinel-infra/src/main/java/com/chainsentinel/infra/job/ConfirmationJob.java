package com.chainsentinel.infra.job;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import com.chainsentinel.core.service.EventConfirmationService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "chainsentinel.confirmation", name = "enabled", havingValue = "true", matchIfMissing
	= true)
public class ConfirmationJob {

	private static final Logger log = LoggerFactory.getLogger(ConfirmationJob.class);
	private static final String METRIC_JOB_RUN_TOTAL = "chainsentinel_job_run_total";
	private static final String METRIC_JOB_RUN_DURATION = "chainsentinel_job_run_duration";
	private static final String METRIC_JOB_RUNNING = "chainsentinel_job_running";
	private static final String JOB_TAG = "confirmation";

	private final EventConfirmationService eventConfirmationService;
	private final MeterRegistry meterRegistry;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicInteger runningGauge = new AtomicInteger(0);

	public ConfirmationJob(EventConfirmationService eventConfirmationService, MeterRegistry meterRegistry) {
		this.eventConfirmationService = eventConfirmationService;
		this.meterRegistry = meterRegistry;
		Gauge.builder(METRIC_JOB_RUNNING, runningGauge, AtomicInteger::get)
			.tag("job", JOB_TAG)
			.register(meterRegistry);
	}

	@Scheduled(
		fixedDelayString = "${chainsentinel.confirmation.interval-ms:20000}",
		initialDelayString = "${chainsentinel.confirmation.initial-delay-ms:5000}"
	)
	public void run() {
		if (!running.compareAndSet(false, true)) {
			log.warn("confirmation.job.skip reason=already_running");
			meterRegistry.counter(METRIC_JOB_RUN_TOTAL, "job", JOB_TAG, "status", "skipped_running").increment();
			return;
		}
		runningGauge.set(1);
		long startedNs = System.nanoTime();
		String status = "success";
		try {
			int updated = eventConfirmationService.advancePendingConfirmations();
			log.info("confirmation.job.done updated={}", updated);
		} catch (Exception ex) {
			status = "failed";
			log.error("confirmation.job.failed", ex);
		} finally {
			meterRegistry.counter(METRIC_JOB_RUN_TOTAL, "job", JOB_TAG, "status", status).increment();
			meterRegistry.timer(METRIC_JOB_RUN_DURATION, "job", JOB_TAG, "status", status)
				.record(System.nanoTime() - startedNs, TimeUnit.NANOSECONDS);
			runningGauge.set(0);
			running.set(false);
		}
	}

}
