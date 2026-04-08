package com.chainsentinel.infra.rule;

import java.math.BigDecimal;
import java.util.Locale;

import com.chainsentinel.core.rule.model.PriceRuleCondition;
import com.chainsentinel.core.rule.model.PriceRuleOperator;
import com.chainsentinel.core.rule.model.PriceRuleSpec;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PriceRuleConditionParser {

	private final ObjectMapper objectMapper;

	public PriceRuleConditionParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public PriceRuleSpec parse(String conditionJson) {
		if (!StringUtils.hasText(conditionJson)) {
			throw new IllegalArgumentException("condition_json is blank");
		}
		try {
			PriceRuleSpec spec = objectMapper.readValue(conditionJson, PriceRuleSpec.class);
			validate(spec);
			return spec;
		} catch (Exception ex) {
			if (ex instanceof IllegalArgumentException iae) {
				throw iae;
			}
			throw new IllegalArgumentException("Invalid condition_json", ex);
		}
	}

	public String serialize(PriceRuleSpec spec) {
		validate(spec);
		try {
			return objectMapper.writeValueAsString(spec);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Invalid rule object", ex);
		}
	}

	public boolean matches(PriceRuleSpec spec, BigDecimal currentPrice) {
		validate(spec);
		BigDecimal actual = currentPrice == null ? BigDecimal.ZERO : currentPrice;
		BigDecimal threshold = toDecimal(spec.getCondition().getThreshold());
		PriceRuleOperator op = spec.getCondition().getOp();
		int cmp = actual.compareTo(threshold);
		return switch (op) {
			case GT -> cmp > 0;
			case GTE -> cmp >= 0;
			case LT -> cmp < 0;
			case LTE -> cmp <= 0;
		};
	}

	private void validate(PriceRuleSpec spec) {
		if (spec == null) {
			throw new IllegalArgumentException("Rule is null");
		}
		if (spec.getVersion() != 1) {
			throw new IllegalArgumentException("Unsupported version: " + spec.getVersion());
		}
		if (!"PRICE".equalsIgnoreCase(spec.getType()) && !"PRICE_THRESHOLD".equalsIgnoreCase(spec.getType())) {
			throw new IllegalArgumentException("Unsupported type: " + spec.getType());
		}
		PriceRuleCondition condition = spec.getCondition();
		if (condition == null) {
			throw new IllegalArgumentException("condition is required");
		}
		if (!StringUtils.hasText(condition.getSymbol())) {
			throw new IllegalArgumentException("condition.symbol is required");
		}
		if (condition.getOp() == null) {
			throw new IllegalArgumentException("condition.op is required");
		}
		if (!StringUtils.hasText(condition.getThreshold())) {
			throw new IllegalArgumentException("condition.threshold is required");
		}
		toDecimal(condition.getThreshold());
		if (condition.getCooldownSec() != null && condition.getCooldownSec() < 0) {
			throw new IllegalArgumentException("condition.cooldownSec must be >= 0");
		}
		condition.setSymbol(condition.getSymbol().trim().toUpperCase(Locale.ROOT));
	}

	private BigDecimal toDecimal(String value) {
		try {
			return new BigDecimal(value.trim());
		} catch (Exception ex) {
			throw new IllegalArgumentException("condition.threshold is invalid decimal: " + value, ex);
		}
	}

}
