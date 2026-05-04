package com.chainsentinel.infra.service;

public record TradeProviderSubmitResult(
	boolean success,
	String providerOrderId,
	String status,
	String errorCode,
	String errorMessage
) {
}
