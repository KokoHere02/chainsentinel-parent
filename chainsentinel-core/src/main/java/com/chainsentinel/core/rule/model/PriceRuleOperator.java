package com.chainsentinel.core.rule.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum PriceRuleOperator {
	GT("gt"),
	GTE("gte"),
	LT("lt"),
	LTE("lte");

	private final String wireValue;

	PriceRuleOperator(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String wireValue() {
		return wireValue;
	}

	@JsonCreator
	public static PriceRuleOperator fromValue(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		for (PriceRuleOperator op : values()) {
			if (op.wireValue.equals(normalized)) {
				return op;
			}
		}
		throw new IllegalArgumentException("Unsupported price op: " + value);
	}
}