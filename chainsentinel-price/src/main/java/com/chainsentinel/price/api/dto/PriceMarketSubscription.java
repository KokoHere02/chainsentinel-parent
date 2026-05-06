package com.chainsentinel.price.api.dto;

public record PriceMarketSubscription(
	String provider,
	String instId,
	PriceMarketChannel channel,
	Integer depth
) {
}
