package com.chainsentinel.price.stream;

import com.chainsentinel.price.api.dto.PriceInstType;
import java.math.BigDecimal;

public record PriceStreamQuote(
	String providerName,
	String chain,
	PriceInstType instType,
	String baseSymbol,
	String quoteSymbol,
	BigDecimal price,
	long ts
) {
	public String instId() {
		return (baseSymbol + "-" + quoteSymbol).toUpperCase();
	}
}