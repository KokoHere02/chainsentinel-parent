package com.chainsentinel.web.api;

import com.chainsentinel.core.service.AlertDispatchService;
import com.chainsentinel.infra.service.AlertFailureSummaryService;
import com.chainsentinel.infra.service.AlertRetryService;
import com.chainsentinel.web.api.support.ratelimit.RateLimit;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/alerts")
public class InternalAlertController {

	private final AlertFailureSummaryService alertFailureSummaryService;
	private final AlertDispatchService alertDispatchService;
	private final AlertRetryService alertRetryService;

	public InternalAlertController(
		AlertFailureSummaryService alertFailureSummaryService,
		AlertDispatchService alertDispatchService,
		AlertRetryService alertRetryService
	) {
		this.alertFailureSummaryService = alertFailureSummaryService;
		this.alertDispatchService = alertDispatchService;
		this.alertRetryService = alertRetryService;
	}

	@GetMapping("/failure-summary")
	public AlertFailureSummaryService.AlertFailureSummaryView failureSummary() {
		return alertFailureSummaryService.summarize();
	}

	@GetMapping("/last-failure")
	public AlertFailureSummaryService.LastFailureView lastFailure() {
		return alertFailureSummaryService.lastFailure();
	}

	@PostMapping("/{id}/retry")
	@RateLimit(
		name = "internal.alerts.retry-one",
		permits = 5,
		windowSeconds = 10,
		scope = RateLimit.Scope.IP,
		message = "Retry too frequent, retry later"
	)
	public RetryResult retryOne(@PathVariable("id") Long id) {
		boolean ok = alertDispatchService.retryOne(id);
		return new RetryResult(id, ok, Instant.now());
	}

	@PostMapping("/retry-failed")
	@RateLimit(
		name = "internal.alerts.retry-failed",
		permits = 2,
		windowSeconds = 10,
		scope = RateLimit.Scope.IP,
		message = "Batch retry too frequent, retry later"
	)
	public AlertRetryService.BatchRetryResult retryFailed(
		@RequestParam(name = "limit", defaultValue = "100") int limit
	) {
		if (limit < 1 || limit > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 500");
		}
		return alertRetryService.retryFailed(limit);
	}

	public record RetryResult(Long alertId, boolean success, Instant retriedAt) {
	}
}