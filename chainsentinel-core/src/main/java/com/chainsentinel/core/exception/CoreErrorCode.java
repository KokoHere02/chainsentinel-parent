package com.chainsentinel.core.exception;

public enum CoreErrorCode {
	NOT_FOUND,
	DEBUG_ENDPOINT_DISABLED,
	RULE_GOVERNANCE_REJECTED,
	UNKNOWN_ERROR;

	public String value() {
		return name();
	}

	public static CoreErrorCode from(String rawCode) {
		if (rawCode == null || rawCode.isBlank()) {
			return UNKNOWN_ERROR;
		}
		try {
			return CoreErrorCode.valueOf(rawCode.trim());
		} catch (IllegalArgumentException ex) {
			return UNKNOWN_ERROR;
		}
	}
}
