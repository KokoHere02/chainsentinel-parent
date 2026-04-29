package com.chainsentinel.infra.job;

import com.chainsentinel.core.service.AlertDispatchService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertDispatchJob {

	private static final Logger log = LoggerFactory.getLogger(AlertDispatchJob.class);
	private static final String METRIC_JOB_RUN_TOTAL = "chainsentinel_job_run_total";
	private static final String METRIC_JOB_RUN_DURATION = "chainsentinel_job_run_duration";
	private static final String METRIC_JOB_RUNNING = "chainsentinel_job_running";
	private static final String JOB_TAG = "alert_dispatch";

	private final AlertDispatchService alertDispatchService;
	private final MeterRegistry meterRegistry;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicInteger runningGauge = new AtomicInteger(0);

	public AlertDispatchJob(AlertDispatchService alertDispatchService, MeterRegistry meterRegistry) {
		this.alertDispatchService = alertDispatchService;
		this.meterRegistry = meterRegistry;
		Gauge.builder(METRIC_JOB_RUNNING, runningGauge, AtomicInteger::get)
			.tag("job", JOB_TAG)
			.register(meterRegistry);
	}

	@Scheduled(fixedDelayString = "${chainsentinel.alert.dispatch-interval-ms:10000}")
	public void run() {
		if (!running.compareAndSet(false, true)) {
			log.warn("alert.dispatch.job.skip reason=already_running");
			meterRegistry.counter(METRIC_JOB_RUN_TOTAL, "job", JOB_TAG, "status", "skipped_running").increment();
			return;
		}
		runningGauge.set(1);
		long startedNs = System.nanoTime();
		String status = "success";
		try {
			int sent = alertDispatchService.dispatchPending();
			log.info("alert.dispatch.job.done sent={}", sent);
		} catch (Exception ex) {
			status = "failed";
			log.error("alert.dispatch.job.failed", ex);
		} finally {
			meterRegistry.counter(METRIC_JOB_RUN_TOTAL, "job", JOB_TAG, "status", status).increment();
			meterRegistry.timer(METRIC_JOB_RUN_DURATION, "job", JOB_TAG, "status", status)
				.record(System.nanoTime() - startedNs, TimeUnit.NANOSECONDS);
			runningGauge.set(0);
			running.set(false);
		}
	}

}
