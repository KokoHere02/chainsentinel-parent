package com.chainsentinel.core.exception;

public class NotFoundException extends AppException {

	public NotFoundException(String message) {
		super("NOT_FOUND", 404, message);
	}
}