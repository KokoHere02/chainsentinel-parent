package com.chainsentinel.core.exception;

public class AppException extends RuntimeException {

	private final CoreErrorCode errorCode;
	private final int status;

	public AppException(CoreErrorCode errorCode, int status, String message) {
		super(message);
		this.errorCode = errorCode == null ? CoreErrorCode.UNKNOWN_ERROR : errorCode;
		this.status = status;
	}

	public AppException(String code, int status, String message) {
		this(CoreErrorCode.from(code), status, message);
	}

	public String getCode() {
		return errorCode.value();
	}

	public CoreErrorCode getErrorCode() {
		return errorCode;
	}

	public int getStatus() {
		return status;
	}
}
