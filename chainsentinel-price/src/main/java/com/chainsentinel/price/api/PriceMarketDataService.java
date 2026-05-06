package com.chainsentinel.price.api;

import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import java.util.List;

public interface PriceMarketDataService {

	PriceOrderBook getOrderBook(String provider, String instId, int depth);

	List<PricePublicTrade> getRecentPublicTrades(String provider, String instId, int limit);
}
