package com.chainsentinel.price.api.dto;

import java.math.BigDecimal;

public record PriceOrderBookLevel(
	BigDecimal price,
	BigDecimal size,
	Integer orderCount
) {
}
