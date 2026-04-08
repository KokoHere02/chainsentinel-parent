package com.chainsentinel.infra.job;

import com.chainsentinel.core.service.AlertDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertDispatchJob {

	private static final Logger log = LoggerFactory.getLogger(AlertDispatchJob.class);

	private final AlertDispatchService alertDispatchService;

	public AlertDispatchJob(AlertDispatchService alertDispatchService) {
		this.alertDispatchService = alertDispatchService;
	}

	@Scheduled(fixedDelayString = "${chainsentinel.alert.dispatch-interval-ms:10000}")
	public void run() {
		int sent = alertDispatchService.dispatchPending();
		log.info("alert.dispatch.job.done sent={}", sent);
	}

}
