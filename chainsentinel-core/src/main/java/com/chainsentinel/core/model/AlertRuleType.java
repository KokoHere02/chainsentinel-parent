package com.chainsentinel.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum AlertRuleType {
	EVENT,
	PRICE_THRESHOLD;

	@JsonValue
	public String wireValue() {
		return name();
	}

	@JsonCreator
	public static AlertRuleType fromValue(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "EVENT", "ADDRESS", "AMOUNT", "FREQUENCY" -> EVENT;
			case "PRICE_THRESHOLD", "PRICE" -> PRICE_THRESHOLD;
			default -> throw new IllegalArgumentException("Unsupported rule type: " + value);
		};
	}
}
