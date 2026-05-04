package com.chainsentinel.core.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeFillView(
	Long id,
	Long orderId,
	String providerFillId,
	String symbol,
	String side,
	BigDecimal price,
	BigDecimal quantity,
	BigDecimal fee,
	String feeCurrency,
	Instant filledAt
) {
}
