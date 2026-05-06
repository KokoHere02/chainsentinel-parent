package com.chainsentinel.price.provider.okx.dto;

import com.chainsentinel.price.api.dto.PricePublicTrade;
import java.util.List;

public record OkxPublicTradeResponse(
	List<PricePublicTrade> trades
) {
}
