package com.chainsentinel.infra.job;

import com.chainsentinel.core.service.EventConfirmationService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "chainsentinel.confirmation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConfirmationJob {

private static final Logger log = LoggerFactory.getLogger(ConfirmationJob.class);

private final EventConfirmationService eventConfirmationService;
private final AtomicBoolean running = new AtomicBoolean(false);

public ConfirmationJob(EventConfirmationService eventConfirmationService) {
this.eventConfirmationService = eventConfirmationService;
}

@Scheduled(
fixedDelayString = "${chainsentinel.confirmation.interval-ms:20000}",
initialDelayString = "${chainsentinel.confirmation.initial-delay-ms:5000}"
)
public void run() {
if (!running.compareAndSet(false, true)) {
log.warn("Skip confirmation run because previous run is still in progress");
return;
}
try {
int updated = eventConfirmationService.advancePendingConfirmations();
log.info("Confirmation job finished: updated={}", updated);
} catch (Exception ex) {
log.error("Confirmation job failed", ex);
} finally {
running.set(false);
}
}
}
