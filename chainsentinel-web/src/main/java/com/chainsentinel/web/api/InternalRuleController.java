package com.chainsentinel.web.api;

import com.chainsentinel.infra.service.PriceRuleEvaluatorService;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/rules")
public class InternalRuleController {

	private final PriceRuleEvaluatorService priceRuleEvaluatorService;

	public InternalRuleController(PriceRuleEvaluatorService priceRuleEvaluatorService) {
		this.priceRuleEvaluatorService = priceRuleEvaluatorService;
	}

	@PostMapping("/price/evaluate")
	public PriceEvaluateResponse evaluatePriceRules() {
		int created = priceRuleEvaluatorService.evaluateOnce();
		return new PriceEvaluateResponse(created, Instant.now());
	}

	public record PriceEvaluateResponse(int createdCount, Instant executedAt) {
	}
}
