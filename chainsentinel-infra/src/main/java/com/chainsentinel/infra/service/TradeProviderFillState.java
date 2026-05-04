package com.chainsentinel.infra.service;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeProviderFillState(
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
