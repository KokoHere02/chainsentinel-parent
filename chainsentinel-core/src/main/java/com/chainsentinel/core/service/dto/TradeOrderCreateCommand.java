package com.chainsentinel.core.service.dto;

import java.math.BigDecimal;

public record TradeOrderCreateCommand(
	Long accountId,
	String symbol,
	String side,
	String orderType,
	BigDecimal price,
	BigDecimal quantity,
	BigDecimal quoteAmount,
	String clientOrderId
) {
}
