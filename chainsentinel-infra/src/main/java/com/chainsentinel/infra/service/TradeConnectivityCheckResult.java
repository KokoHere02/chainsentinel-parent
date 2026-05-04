package com.chainsentinel.infra.service;

public record TradeConnectivityCheckResult(
	boolean success,
	String message
) {
}
