package com.chainsentinel.infra.service;

import java.math.BigDecimal;

public record TradeProviderOrderState(
	boolean success,
	String status,
	String providerOrderId,
	BigDecimal avgFillPrice,
	BigDecimal filledQuantity,
	BigDecimal filledAmount,
	String errorCode,
	String errorMessage
) {
}
