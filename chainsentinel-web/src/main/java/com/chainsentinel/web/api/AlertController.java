package com.chainsentinel.web.api;

import com.chainsentinel.core.service.AlertDispatchService;
import com.chainsentinel.core.service.AlertQueryService;
import com.chainsentinel.core.service.dto.AlertQuery;
import com.chainsentinel.core.service.dto.AlertView;
import com.chainsentinel.web.api.support.ratelimit.RateLimit;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

	private final AlertQueryService alertQueryService;
	private final AlertDispatchService alertDispatchService;

	public AlertController(AlertQueryService alertQueryService, AlertDispatchService alertDispatchService) {
		this.alertQueryService = alertQueryService;
		this.alertDispatchService = alertDispatchService;
	}

	@GetMapping
	public Page<AlertView> list(
		@RequestParam(name = "sendStatus", required = false) String sendStatus,
		@RequestParam(name = "severity", required = false) String severity,
		@RequestParam(name = "ruleId", required = false) Long ruleId,
		@RequestParam(name = "sentAtFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant sentAtFrom,
		@RequestParam(name = "sentAtTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant sentAtTo,
		@RequestParam(name = "page", defaultValue = "0") int page,
		@RequestParam(name = "size", defaultValue = "20") int size
	) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
		return alertQueryService.query(new AlertQuery(sendStatus, severity, ruleId, sentAtFrom, sentAtTo), pageable);
	}

	@PostMapping("/retry/{id}")
	@RateLimit(
		name = "alerts.retry-one",
		permits = 5,
		windowSeconds = 10,
		scope = RateLimit.Scope.IP,
		message = "Retry too frequent, retry later"
	)
	public RetryResponse retry(@PathVariable("id") Long id) {
		boolean ok = alertDispatchService.retryOne(id);
		return new RetryResponse(ok);
	}

	@PostMapping("/handler")
	public Map<String, String> handlerAlert(@RequestBody String json) {
		log.info("alert.handler.received payloadBytes={}", json == null ? 0 : json.length());
		return Map.of("code", "200");
	}

	public record RetryResponse(boolean success) {
	}
}


