package com.chainsentinel.price.api.dto;

import java.math.BigDecimal;

public record PricePublicTrade(
	String provider,
	String instId,
	String tradeId,
	BigDecimal price,
	BigDecimal size,
	String side,
	Long ts
) {
}
