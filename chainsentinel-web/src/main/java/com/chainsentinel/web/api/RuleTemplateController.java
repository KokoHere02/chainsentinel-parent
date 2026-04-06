package com.chainsentinel.web.api;

import com.chainsentinel.core.model.AlertRuleType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rule-templates")
public class RuleTemplateController {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final List<RuleTemplateView> TEMPLATES = List.of(
		new RuleTemplateView(
			"PRICE_BREAKOUT",
			"Price Breakout",
			"Trigger when price reaches or breaks the configured threshold.",
			AlertRuleType.PRICE_THRESHOLD,
			toJsonNode(Map.of(
				"version", 1,
				"type", "PRICE",
				"condition", Map.of(
					"symbol", "BTC-USDT",
					"op", "gte",
					"threshold", "100000",
					"cooldownSec", 300
				)
			)),
			"HIGH",
			true
		),
		new RuleTemplateView(
			"ADDRESS_LARGE_TRANSFER",
			"Address Large Transfer",
			"Trigger when on-chain transfer amount reaches the configured threshold.",
			AlertRuleType.AMOUNT,
			toJsonNode(Map.of(
				"version", 1,
				"type", "EVENT",
				"condition", Map.of(
					"all", List.of(
						Map.of("field", "chain", "op", "eq", "value", "ETH"),
						Map.of("field", "network", "op", "eq", "value", "mainnet"),
						Map.of("field", "amount", "op", "gte", "value", "100")
					)
				)
			)),
			"HIGH",
			true
		),
		new RuleTemplateView(
			"CONTRACT_INTERACTION",
			"Contract Interaction",
			"Trigger when target address interacts with the configured contract address.",
			AlertRuleType.ADDRESS,
			toJsonNode(Map.of(
				"version", 1,
				"type", "EVENT",
				"condition", Map.of(
					"all", List.of(
						Map.of("field", "chain", "op", "eq", "value", "ETH"),
						Map.of("field", "network", "op", "eq", "value", "mainnet"),
						Map.of("field", "to_address", "op", "eq", "value", "0x0000000000000000000000000000000000000000")
					)
				)
			)),
			"MEDIUM",
			true
		)
	);

	@GetMapping
	public List<RuleTemplateView> list() {
		return TEMPLATES;
	}

	public static Optional<RuleTemplateView> findByKey(String key) {
		return TEMPLATES.stream()
			.filter(template -> template.key().equalsIgnoreCase(key))
			.findFirst();
	}

	private static JsonNode toJsonNode(Map<String, Object> map) {
		return OBJECT_MAPPER.valueToTree(map);
	}

	public record RuleTemplateView(
		String key,
		String name,
		String description,
		AlertRuleType type,
		JsonNode condition,
		String severity,
		Boolean enabled
	) {
	}
}
