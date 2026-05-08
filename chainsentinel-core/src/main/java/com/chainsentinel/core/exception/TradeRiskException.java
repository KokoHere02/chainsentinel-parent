package com.chainsentinel.core.exception;

public class TradeRiskException extends AppException {

	public TradeRiskException(CoreErrorCode errorCode, String message) {
		super(errorCode, 400, message);
	}

	public TradeRiskException(CoreErrorCode errorCode, int status, String message) {
		super(errorCode, status, message);
	}
}
