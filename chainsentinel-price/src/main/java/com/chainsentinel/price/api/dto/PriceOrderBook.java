package com.chainsentinel.price.api.dto;

import java.util.List;

public record PriceOrderBook(
	String provider,
	String instId,
	Long ts,
	Long seqId,
	Long checksum,
	List<PriceOrderBookLevel> asks,
	List<PriceOrderBookLevel> bids
) {
}
