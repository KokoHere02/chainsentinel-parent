package com.chainsentinel.infra.job;

import com.chainsentinel.infra.service.PriceRuleEvaluatorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceRuleEvaluationJob {

private static final Logger log = LoggerFactory.getLogger(PriceRuleEvaluationJob.class);

private final PriceRuleEvaluatorService priceRuleEvaluatorService;
private final AtomicBoolean running = new AtomicBoolean(false);

public PriceRuleEvaluationJob(PriceRuleEvaluatorService priceRuleEvaluatorService) {
this.priceRuleEvaluatorService = priceRuleEvaluatorService;
}

@Scheduled(fixedDelayString = "${chainsentinel.alert.price-eval-interval-ms:15000}")
public void run() {
if (!running.compareAndSet(false, true)) {
log.warn("price.rule.job.skip previous run still in progress");
return;
}

try {
int created = priceRuleEvaluatorService.evaluateOnce();
log.info("price.rule.job.done created={}", created);
} catch (Exception ex) {
log.error("price.rule.job.failed", ex);
} finally {
running.set(false);
}
}
}
