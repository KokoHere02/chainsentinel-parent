package com.chainsentinel.marketgateway.api;

import org.springframework.http.HttpStatus;

public class MarketGatewayException extends RuntimeException {

	private final HttpStatus status;

	private MarketGatewayException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public static MarketGatewayException notFound(String message) {
		return new MarketGatewayException(HttpStatus.NOT_FOUND, message);
	}

	public static MarketGatewayException badRequest(String message) {
		return new MarketGatewayException(HttpStatus.BAD_REQUEST, message);
	}

	public HttpStatus status() {
		return status;
	}
}
