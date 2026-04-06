package com.chainsentinel.web.api;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.model.TokenType;
import com.chainsentinel.core.rule.model.PriceRuleSpec;
import com.chainsentinel.core.service.AlertRuleService;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
import com.chainsentinel.infra.rule.RuleConditionJsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
@Validated
public class RuleDebugController {

	private final AlertRuleService alertRuleService;
	private final RuleConditionJsonParser ruleConditionJsonParser;
	private final EventRuleConditionParser eventRuleConditionParser;

	public RuleDebugController(
		AlertRuleService alertRuleService,
		RuleConditionJsonParser ruleConditionJsonParser,
		EventRuleConditionParser eventRuleConditionParser
	) {
		this.alertRuleService = alertRuleService;
		this.ruleConditionJsonParser = ruleConditionJsonParser;
		this.eventRuleConditionParser = eventRuleConditionParser;
	}

	@PostMapping("/{id}/test-match")
	public RuleTestMatchResponse testMatch(@PathVariable Long id, @RequestBody @Valid RuleTestMatchRequest request) {
		AlertRuleView rule = alertRuleService.getById(id);
		boolean matched = switch (rule.type()) {
			case PRICE_THRESHOLD -> matchPriceRule(rule, request.sample());
			case ADDRESS, AMOUNT -> matchEventRule(rule, request.sample());
			default -> throw new IllegalArgumentException("Unsupported rule type: " + rule.type());
		};
		return new RuleTestMatchResponse(matched, matched ? "matched" : "not_matched");
	}

	private boolean matchPriceRule(AlertRuleView rule, JsonNode sample) {
		PriceRuleSpec spec = ruleConditionJsonParser.parsePrice(rule.conditionJson());
		JsonNode currentPriceNode = sample.get("currentPrice");
		if (currentPriceNode == null || currentPriceNode.isNull()) {
			throw new IllegalArgumentException("sample.currentPrice is required for PRICE_THRESHOLD");
		}
		BigDecimal currentPrice = new BigDecimal(currentPriceNode.asText());
		return ruleConditionJsonParser.matchPrice(spec, currentPrice);
	}

	private boolean matchEventRule(AlertRuleView rule, JsonNode sample) {
		return eventRuleConditionParser.matches(rule.conditionJson(), toSampleEvent(sample));
	}

	private AssetEventEntity toSampleEvent(JsonNode sample) {
		AssetEventEntity event = new AssetEventEntity();
		event.setChain(readText(sample, "chain"));
		event.setNetwork(readText(sample, "network"));
		event.setFromAddress(readText(sample, "fromAddress", "from_address"));
		event.setToAddress(readText(sample, "toAddress", "to_address"));
		event.setTokenContract(readText(sample, "tokenContract", "token_contract"));
		event.setSymbol(readText(sample, "symbol"));
		event.setAmount(readText(sample, "amount"));
		event.setTokenType(readEnum(sample, TokenType.class, "tokenType", "token_type"));
		event.setStatus(readEnum(sample, EventStatus.class, "status"));
		event.setOccurredAt(Instant.now());
		event.setIngestedAt(Instant.now());
		return event;
	}

	private String readText(JsonNode sample, String... keys) {
		for (String key : keys) {
			JsonNode node = sample.get(key);
			if (node != null && !node.isNull()) {
				return node.asText();
			}
		}
		return null;
	}

	private <E extends Enum<E>> E readEnum(JsonNode sample, Class<E> enumType, String... keys) {
		String text = readText(sample, keys);
		if (text == null || text.isBlank()) {
			return null;
		}
		return Enum.valueOf(enumType, text.trim().toUpperCase());
	}

	public record RuleTestMatchRequest(@NotNull JsonNode sample) {
	}

	public record RuleTestMatchResponse(boolean matched, String reason) {
	}
}
