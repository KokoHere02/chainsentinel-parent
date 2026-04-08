package com.chainsentinel.web.api;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.service.AlertRuleService;
import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rule-templates")
public class RuleTemplateController {

	private final AlertRuleService alertRuleService;
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

	public RuleTemplateController(AlertRuleService alertRuleService) {
		this.alertRuleService = alertRuleService;
	}

	@GetMapping
	public List<RuleTemplateView> list() {
		return TEMPLATES;
	}

	@PostMapping("/from-template")
	public AlertRuleView createFromTemplate(@RequestBody @Valid RuleController.RuleCreateFromTemplateRequest request) {
		RuleTemplateView template = RuleTemplateController.findByKey(request.templateKey())
			.orElseThrow(() -> new IllegalArgumentException("Unknown templateKey: " + request.templateKey()));
		JsonNode condition = mergeTemplateCondition(template.condition(), request.conditionOverrides());
		return alertRuleService.create(new AlertRuleCreateCommand(
			request.name() == null ? template.name() : request.name(),
			template.type(),
			condition,
			request.severity() == null ? template.severity() : request.severity(),
			request.enabled() == null ? template.enabled() : request.enabled()
		));
	}

	private JsonNode mergeTemplateCondition(JsonNode templateCondition, JsonNode conditionOverrides) {
		if (conditionOverrides == null || conditionOverrides.isNull()) {
			return templateCondition.deepCopy();
		}
		if (!conditionOverrides.isObject()) {
			throw new IllegalArgumentException("conditionOverrides must be a JSON object");
		}
		ObjectNode merged = templateCondition.deepCopy();
		mergeObjectNode(merged, conditionOverrides);
		return merged;
	}

	private void mergeObjectNode(ObjectNode target, JsonNode source) {
		source.fields().forEachRemaining(entry -> {
			String fieldName = entry.getKey();
			JsonNode sourceValue = entry.getValue();
			JsonNode targetValue = target.get(fieldName);
			if (sourceValue.isObject() && targetValue != null && targetValue.isObject()) {
				mergeObjectNode((ObjectNode) targetValue, sourceValue);
				return;
			}
			target.set(fieldName, OBJECT_MAPPER.valueToTree(sourceValue));
		});
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
