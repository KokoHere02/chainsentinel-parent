package com.chainsentinel.core.rule.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum EventRuleField {
	CHAIN("chain"),
	NETWORK("network"),
	FROM_ADDRESS("from_address"),
	TO_ADDRESS("to_address"),
	TOKEN_TYPE("token_type"),
	TOKEN_CONTRACT("token_contract"),
	SYMBOL("symbol"),
	AMOUNT("amount"),
	STATUS("status");

	private final String wireValue;

	EventRuleField(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String wireValue() {
		return wireValue;
	}

	@JsonCreator
	public static EventRuleField fromValue(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		for (EventRuleField field : values()) {
			if (field.wireValue.equals(normalized)) {
				return field;
			}
		}
		throw new IllegalArgumentException("Unsupported field: " + value);
	}
}
