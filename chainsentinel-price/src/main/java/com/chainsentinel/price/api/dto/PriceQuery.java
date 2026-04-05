package com.chainsentinel.price.api.dto;

public record PriceQuery(
	String chain,
	PriceInstType instType,
	String symbol,
	String quoteSymbol,
	String tokenAddress
) {
	public String normalizedInstId() {
		return (symbol + "-" + quoteSymbol).toUpperCase();
	}

	public String normalizedInstType() {
		return instType == null ? "" : instType.name();
	}
}
