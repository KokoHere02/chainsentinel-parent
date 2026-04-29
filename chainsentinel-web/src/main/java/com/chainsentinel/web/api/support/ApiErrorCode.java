package com.chainsentinel.web.api.support;

public enum ApiErrorCode {
	VALIDATION_ERROR,
	TYPE_MISMATCH,
	INVALID_REQUEST_BODY,
	INVALID_ARGUMENT,
	HTTP_ERROR,
	INTERNAL_ERROR;

	public String value() {
		return name();
	}
}
