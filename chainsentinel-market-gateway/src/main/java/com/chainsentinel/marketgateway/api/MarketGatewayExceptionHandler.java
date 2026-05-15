package com.chainsentinel.marketgateway.api;

import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(basePackages = "com.chainsentinel.marketgateway")
public class MarketGatewayExceptionHandler {

	@ExceptionHandler(MarketGatewayException.class)
	public ResponseEntity<ApiError> handleMarketGatewayException(MarketGatewayException ex) {
		return ResponseEntity.status(ex.status()).body(new ApiError(ex.status().value(), ex.getMessage(), Instant.now().toEpochMilli()));
	}

	@ExceptionHandler({
		IllegalArgumentException.class,
		MethodArgumentNotValidException.class,
		HandlerMethodValidationException.class
	})
	public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
		return ResponseEntity.badRequest().body(new ApiError(400, ex.getMessage(), Instant.now().toEpochMilli()));
	}

	public record ApiError(int status, String message, long timestamp) {
	}
}
