package com.chainsentinel.web.api;

import com.chainsentinel.infra.service.PriceRuleEvaluatorService;
import com.chainsentinel.infra.service.RuleHitStatsService;
import com.chainsentinel.web.api.support.ratelimit.RateLimit;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/rules")
public class InternalRuleController {

	private final PriceRuleEvaluatorService priceRuleEvaluatorService;
	private final RuleHitStatsService ruleHitStatsService;

	public InternalRuleController(
		PriceRuleEvaluatorService priceRuleEvaluatorService,
		RuleHitStatsService ruleHitStatsService
	) {
		this.priceRuleEvaluatorService = priceRuleEvaluatorService;
		this.ruleHitStatsService = ruleHitStatsService;
	}

	@PostMapping("/price/evaluate")
	@RateLimit(
		name = "internal.rules.price.evaluate",
		permits = 3,
		windowSeconds = 10,
		scope = RateLimit.Scope.IP,
		message = "Evaluate too frequent, retry later"
	)
	public PriceEvaluateResponse evaluatePriceRules() {
		int created = priceRuleEvaluatorService.evaluateOnce();
		return new PriceEvaluateResponse(created, Instant.now());
	}

	@GetMapping("/hit-stats")
	public List<RuleHitStatsService.RuleHitStatsView> hitStats(
		@RequestParam(name = "enabledOnly", defaultValue = "true") boolean enabledOnly
	) {
		return ruleHitStatsService.list(enabledOnly);
	}

	public record PriceEvaluateResponse(int createdCount, Instant executedAt) {
	}
}