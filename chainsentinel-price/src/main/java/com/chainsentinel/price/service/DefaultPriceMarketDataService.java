package com.chainsentinel.price.service;

import com.chainsentinel.price.api.PriceMarketDataService;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import com.chainsentinel.price.provider.okx.OkxApiClient;
import com.chainsentinel.price.provider.okx.dto.OkxOrderBookResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DefaultPriceMarketDataService implements PriceMarketDataService {

	private final OkxApiClient okxApiClient;

	public DefaultPriceMarketDataService(OkxApiClient okxApiClient) {
		this.okxApiClient = okxApiClient;
	}

	@Override
	public PriceOrderBook getOrderBook(String provider, String instId, int depth) {
		String normalizedProvider = normalizeProvider(provider);
		OkxOrderBookResponse response = okxApiClient.fetchOrderBook(instId, depth)
			.orElse(new OkxOrderBookResponse(instId, null, null, null, List.of(), List.of()));
		return new PriceOrderBook(
			normalizedProvider,
			response.instId(),
			response.ts(),
			response.seqId(),
			response.checksum(),
			response.asks(),
			response.bids()
		);
	}

	@Override
	public List<PricePublicTrade> getRecentPublicTrades(String provider, String instId, int limit) {
		normalizeProvider(provider);
		return okxApiClient.fetchRecentPublicTrades(instId, limit);
	}

	private String normalizeProvider(String provider) {
		if (provider == null || provider.isBlank() || "okx".equalsIgnoreCase(provider) || "okx_ws".equalsIgnoreCase(provider)) {
			return "okx";
		}
		throw new IllegalArgumentException("unsupported provider: " + provider);
	}
}
