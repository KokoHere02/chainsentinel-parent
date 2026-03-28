package com.chainsentinel.infra.job;

import com.chainsentinel.core.service.AlertDispatchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertDispatchJob {

    private final AlertDispatchService alertDispatchService;

    public AlertDispatchJob(AlertDispatchService alertDispatchService) {
        this.alertDispatchService = alertDispatchService;
    }

    @Scheduled(fixedDelayString = "${chainsentinel.alert.dispatch-interval-ms:10000}")
    public void run() {
        alertDispatchService.dispatchPending();
    }
}
