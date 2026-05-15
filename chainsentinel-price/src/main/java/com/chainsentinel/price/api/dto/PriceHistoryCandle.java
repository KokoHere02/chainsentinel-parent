package com.chainsentinel.price.api.dto;

import java.math.BigDecimal;

public record PriceHistoryCandle(
	long ts,
	BigDecimal closePrice
) {
}
