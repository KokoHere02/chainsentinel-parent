package com.chainsentinel.web.auth;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {

	private final AuthErrorCode errorCode;
	private final HttpStatus status;

	public AuthException(AuthErrorCode errorCode, HttpStatus status, String message) {
		super(message);
		this.errorCode = errorCode;
		this.status = status;
	}

	public AuthErrorCode getErrorCode() {
		return errorCode;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
