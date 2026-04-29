package com.chainsentinel.infra.job;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import com.chainsentinel.infra.service.PriceRuleEvaluatorService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceRuleEvaluationJob {

	private static final Logger log = LoggerFactory.getLogger(PriceRuleEvaluationJob.class);
	private static final String METRIC_JOB_RUN_TOTAL = "chainsentinel_job_run_total";
	private static final String METRIC_JOB_RUN_DURATION = "chainsentinel_job_run_duration";
	private static final String METRIC_JOB_RUNNING = "chainsentinel_job_running";
	private static final String JOB_TAG = "price_rule_evaluation";

	private final PriceRuleEvaluatorService priceRuleEvaluatorService;
	private final MeterRegistry meterRegistry;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicInteger runningGauge = new AtomicInteger(0);

	public PriceRuleEvaluationJob(PriceRuleEvaluatorService priceRuleEvaluatorService, MeterRegistry meterRegistry) {
		this.priceRuleEvaluatorService = priceRuleEvaluatorService;
		this.meterRegistry = meterRegistry;
		Gauge.builder(METRIC_JOB_RUNNING, runningGauge, AtomicInteger::get)
			.tag("job", JOB_TAG)
			.register(meterRegistry);
	}

	@Scheduled(fixedDelayString = "${chainsentinel.alert.price-eval-interval-ms:15000}")
	public void run() {
		if (!running.compareAndSet(false, true)) {
			log.warn("price.rule.job.skip previous run still in progress");
			meterRegistry.counter(METRIC_JOB_RUN_TOTAL, "job", JOB_TAG, "status", "skipped_running").increment();
			return;
		}
		runningGauge.set(1);
		long startedNs = System.nanoTime();
		String status = "success";

		try {
			int created = priceRuleEvaluatorService.evaluateOnce();
			log.info("price.rule.job.done created={}", created);
		} catch (Exception ex) {
			status = "failed";
			log.error("price.rule.job.failed", ex);
		} finally {
			meterRegistry.counter(METRIC_JOB_RUN_TOTAL, "job", JOB_TAG, "status", status).increment();
			meterRegistry.timer(METRIC_JOB_RUN_DURATION, "job", JOB_TAG, "status", status)
				.record(System.nanoTime() - startedNs, TimeUnit.NANOSECONDS);
			runningGauge.set(0);
			running.set(false);
		}
	}

}
