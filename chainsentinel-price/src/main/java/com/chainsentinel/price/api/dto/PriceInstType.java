package com.chainsentinel.price.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum PriceInstType {
	SPOT,
	MARGIN,
	SWAP,
	FUTURES,
	OPTION;

	@JsonValue
	public String wireValue() {
		return name();
	}

	@JsonCreator
	public static PriceInstType fromValue(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		for (PriceInstType type : values()) {
			if (type.name().equals(normalized)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unsupported instType: " + value);
	}
}
