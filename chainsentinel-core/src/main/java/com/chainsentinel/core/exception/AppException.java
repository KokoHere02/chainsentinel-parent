package com.chainsentinel.core.exception;

public class AppException extends RuntimeException {

	private final String code;
	private final int status;

	public AppException(String code, int status, String message) {
		super(message);
		this.code = code;
		this.status = status;
	}

	public String getCode() {
		return code;
	}

	public int getStatus() {
		return status;
	}
}