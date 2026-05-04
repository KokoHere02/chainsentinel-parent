package com.chainsentinel.core.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeOrderView(
	Long id,
	Long accountId,
	String clientOrderId,
	String provider,
	String marketType,
	String symbol,
	String side,
	String orderType,
	BigDecimal price,
	BigDecimal quantity,
	BigDecimal quoteAmount,
	String status,
	String providerOrderId,
	BigDecimal avgFillPrice,
	BigDecimal filledQuantity,
	BigDecimal filledAmount,
	String errorCode,
	String errorMessage,
	Long createdBy,
	Instant createdAt,
	Instant updatedAt
) {
}
