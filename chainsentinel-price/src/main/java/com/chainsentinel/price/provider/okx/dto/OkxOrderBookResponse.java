package com.chainsentinel.price.provider.okx.dto;

import com.chainsentinel.price.api.dto.PriceOrderBookLevel;
import java.util.List;

public record OkxOrderBookResponse(
	String instId,
	Long ts,
	Long seqId,
	Long checksum,
	List<PriceOrderBookLevel> asks,
	List<PriceOrderBookLevel> bids
) {
}
