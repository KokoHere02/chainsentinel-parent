package com.chainsentinel.infra.service;

public record TradeProviderCancelResult(
	boolean success,
	String status,
	String errorCode,
	String errorMessage
) {
}
