package com.chainsentinel.price.service;

import com.chainsentinel.price.api.PriceMarketDataService;
import com.chainsentinel.price.api.PublicMarketDataClient;
import com.chainsentinel.price.api.PublicMarketDataClientRouter;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DefaultPriceMarketDataService implements PriceMarketDataService {

	private final PublicMarketDataClientRouter marketDataClientRouter;

	public DefaultPriceMarketDataService(PublicMarketDataClientRouter marketDataClientRouter) {
		this.marketDataClientRouter = marketDataClientRouter;
	}

	@Override
	public PriceOrderBook getOrderBook(String provider, String instId, int depth) {
		PublicMarketDataClient client = marketDataClientRouter.resolve(provider);
		return client.getOrderBook(instId, depth)
			.orElse(new PriceOrderBook(client.provider(), instId, null, null, null, List.of(), List.of()));
	}

	@Override
	public List<PricePublicTrade> getRecentPublicTrades(String provider, String instId, int limit) {
		return marketDataClientRouter.resolve(provider).getRecentPublicTrades(instId, limit);
	}
}
