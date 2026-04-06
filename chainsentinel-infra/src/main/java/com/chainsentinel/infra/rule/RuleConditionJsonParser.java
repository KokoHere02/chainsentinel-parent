package com.chainsentinel.infra.rule;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.rule.model.EventRuleSpec;
import com.chainsentinel.core.rule.model.PriceRuleSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class RuleConditionJsonParser {

private static final int MAX_COOLDOWN_SEC = 86400;

private final ObjectMapper objectMapper;
private final EventRuleConditionParser eventRuleConditionParser;
private final PriceRuleConditionParser priceRuleConditionParser;

public RuleConditionJsonParser(
ObjectMapper objectMapper,
EventRuleConditionParser eventRuleConditionParser,
PriceRuleConditionParser priceRuleConditionParser
) {
this.objectMapper = objectMapper;
this.eventRuleConditionParser = eventRuleConditionParser;
this.priceRuleConditionParser = priceRuleConditionParser;
}

public String serialize(AlertRuleType type, JsonNode condition) {
if (type == null) {
throw new IllegalArgumentException("Rule type is required");
}
if (condition == null || condition.isNull()) {
throw new IllegalArgumentException("condition is required");
}
return switch (type) {
case ADDRESS, AMOUNT -> eventRuleConditionParser.serialize(toEventRuleSpec(condition));
case PRICE_THRESHOLD -> priceRuleConditionParser.serialize(toNormalizedPriceRuleSpec(condition));
default -> throw new IllegalArgumentException("Unsupported rule type for parser: " + type);
};
}

public EventRuleSpec parseEvent(String conditionJson) {
return eventRuleConditionParser.parse(conditionJson);
}

public PriceRuleSpec parsePrice(String conditionJson) {
return priceRuleConditionParser.parse(conditionJson);
}

public boolean matchPrice(PriceRuleSpec spec, java.math.BigDecimal currentPrice) {
return priceRuleConditionParser.matches(spec, currentPrice);
}

private EventRuleSpec toEventRuleSpec(JsonNode node) {
return objectMapper.convertValue(node, EventRuleSpec.class);
}

private PriceRuleSpec toPriceRuleSpec(JsonNode node) {
return objectMapper.convertValue(node, PriceRuleSpec.class);
}

private PriceRuleSpec toNormalizedPriceRuleSpec(JsonNode node) {
PriceRuleSpec spec = toPriceRuleSpec(node);
if (spec == null || spec.getCondition() == null) {
return spec;
}
Integer cooldownSec = spec.getCondition().getCooldownSec();
if (cooldownSec == null) {
spec.getCondition().setCooldownSec(0);
return spec;
}
if (cooldownSec > MAX_COOLDOWN_SEC) {
throw new IllegalArgumentException("condition.cooldownSec must be <= " + MAX_COOLDOWN_SEC);
}
return spec;
}
}
