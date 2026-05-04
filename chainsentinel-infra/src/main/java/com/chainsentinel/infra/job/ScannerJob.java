package com.chainsentinel.infra.job;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import com.chainsentinel.infra.config.ScannerProperties;
import com.chainsentinel.core.service.ScannerService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "chainsentinel.scanner", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScannerJob {

	private static final Logger log = LoggerFactory.getLogger(ScannerJob.class);
	private static final String METRIC_JOB_RUN_TOTAL = "chainsentinel_job_run_total";
	private static final String METRIC_JOB_RUN_DURATION = "chainsentinel_job_run_duration";
	private static final String METRIC_JOB_RUNNING = "chainsentinel_job_running";
	private static final String JOB_TAG = "scanner";

	private final ScannerService scannerService;
	private final ScannerProperties scannerProperties;
	private final MeterRegistry meterRegistry;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicInteger runningGauge = new AtomicInteger(0);

	public ScannerJob(ScannerService scannerService, ScannerProperties scannerProperties, MeterRegistry meterRegistry) {
		this.scannerService = scannerService;
		this.scannerProperties = scannerProperties;
		this.meterRegistry = meterRegistry;
		Gauge.builder(METRIC_JOB_RUNNING, runningGauge, AtomicInteger::get)
			.tag("job", JOB_TAG)
			.register(meterRegistry);
	}

	@Scheduled(
		fixedDelayString = "${chainsentinel.scanner.scan-interval-ms:10000}",
		initialDelayString = "${chainsentinel.scanner.initial-delay-ms:3000}"
	)
	public void run() {
		if (!running.compareAndSet(false, true)) {
			log.warn("scanner.job.skip reason=already_running");
			meterRegistry.counter(METRIC_JOB_RUN_TOTAL, "job", JOB_TAG, "status", "skipped_running").increment();
			return;
		}
		runningGauge.set(1);
		long startedNs = System.nanoTime();
		String status = "success";
		try {
			int inserted = scannerService.runOnce();
			log.info("scanner.job.done inserted={}", inserted);
		} catch (Exception ex) {
			status = "failed";
			log.error("scanner.job.failed", ex);
		} finally {
			meterRegistry.counter(METRIC_JOB_RUN_TOTAL, "job", JOB_TAG, "status", status).increment();
			meterRegistry.timer(METRIC_JOB_RUN_DURATION, "job", JOB_TAG, "status", status)
				.record(System.nanoTime() - startedNs, TimeUnit.NANOSECONDS);
			runningGauge.set(0);
			running.set(false);
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		if (!scannerProperties.isStartupRunOnReady()) {
			return;
		}
		run();
	}

}
