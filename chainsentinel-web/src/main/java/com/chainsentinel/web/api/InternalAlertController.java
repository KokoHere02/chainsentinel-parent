package com.chainsentinel.web.api;

import com.chainsentinel.infra.service.AlertFailureSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/alerts")
public class InternalAlertController {

	private final AlertFailureSummaryService alertFailureSummaryService;

	public InternalAlertController(AlertFailureSummaryService alertFailureSummaryService) {
		this.alertFailureSummaryService = alertFailureSummaryService;
	}

	@GetMapping("/failure-summary")
	public AlertFailureSummaryService.AlertFailureSummaryView failureSummary() {
		return alertFailureSummaryService.summarize();
	}
}
