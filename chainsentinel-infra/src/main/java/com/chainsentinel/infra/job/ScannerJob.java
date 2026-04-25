package com.chainsentinel.infra.job;

import java.util.concurrent.atomic.AtomicBoolean;

import com.chainsentinel.core.service.ScannerService;
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

	private final ScannerService scannerService;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public ScannerJob(ScannerService scannerService) {
		this.scannerService = scannerService;
	}

//	@Scheduled(
//		fixedDelayString = "${chainsentinel.scanner.scan-interval-ms:10000}",
//		initialDelayString = "${chainsentinel.scanner.initial-delay-ms:3000}"
//	)
	public void run() {
		if (!running.compareAndSet(false, true)) {
			log.warn("Skip scanner run because previous run is still in progress");
			return;
		}
		try {
			int inserted = scannerService.runOnce();
			log.info("Scanner job finished: inserted={}", inserted);
		} catch (Exception ex) {
			log.error("Scanner job failed", ex);
		} finally {
			running.set(false);
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		run();
	}

}
