package com.chainsentinel.price.api;

import com.chainsentinel.price.api.dto.PriceHistoryCandle;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import java.util.List;
import java.util.Optional;

public interface PublicMarketDataClient {

	String provider();

	boolean supportsProvider(String provider);

	Optional<PriceQuote> getQuote(PriceQuery query);

	Optional<PriceOrderBook> getOrderBook(String instId, int depth);

	List<PricePublicTrade> getRecentPublicTrades(String instId, int limit);

	List<PriceHistoryCandle> getHistoryCandles(String instId, String bar, Long afterTs, int limit);
}
