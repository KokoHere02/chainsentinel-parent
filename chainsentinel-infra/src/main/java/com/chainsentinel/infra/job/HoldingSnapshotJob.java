package com.chainsentinel.infra.job;

import java.util.concurrent.atomic.AtomicBoolean;

import com.chainsentinel.infra.service.AddressHoldingSnapshotService;
import com.chainsentinel.infra.service.AddressHoldingSnapshotService.SnapshotResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
	prefix = "chainsentinel.holding",
	name = {"enabled", "snapshot-schedule-enabled"},
	havingValue = "true"
)
public class HoldingSnapshotJob {

	private static final Logger log = LoggerFactory.getLogger(HoldingSnapshotJob.class);

	private final AddressHoldingSnapshotService addressHoldingSnapshotService;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public HoldingSnapshotJob(AddressHoldingSnapshotService addressHoldingSnapshotService) {
		this.addressHoldingSnapshotService = addressHoldingSnapshotService;
	}

	@Scheduled(
		fixedDelayString = "${chainsentinel.holding.interval-ms:1800000}",
		initialDelayString = "${chainsentinel.holding.initial-delay-ms:15000}"
	)
	public void run() {
		execute("scheduled");
	}

	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		execute("startup");
	}

	private void execute(String trigger) {
		if (!running.compareAndSet(false, true)) {
			log.warn("Skip holding snapshot run because previous run is still in progress (trigger={})", trigger);
			return;
		}
		try {
			SnapshotResult result = addressHoldingSnapshotService.refreshNativeHoldings();
			log.info("Holding snapshot finished: trigger={}, scanned={}, changed={}, failed={}",
				trigger, result.scanned(), result.changed(), result.failed());
		} catch (Exception ex) {
			log.error("Holding snapshot failed: trigger={}", trigger, ex);
		} finally {
			running.set(false);
		}
	}
}
