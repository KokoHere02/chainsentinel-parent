package com.chainsentinel.marketgateway.provider;

import com.chainsentinel.price.api.dto.PriceHistoryCandle;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import com.chainsentinel.price.provider.okx.OkxPublicMarketDataClient;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OkxMarketDataProvider implements MarketDataProvider {

	private static final String PROVIDER_NAME = "okx";
	private static final List<MarketDataCapability> CAPABILITIES = List.of(
		MarketDataCapability.QUOTE,
		MarketDataCapability.ORDER_BOOK,
		MarketDataCapability.TRADES,
		MarketDataCapability.KLINES
	);

	private final OkxPublicMarketDataClient okxPublicMarketDataClient;

	public OkxMarketDataProvider(OkxPublicMarketDataClient okxPublicMarketDataClient) {
		this.okxPublicMarketDataClient = okxPublicMarketDataClient;
	}

	@Override
	public String provider() {
		return PROVIDER_NAME;
	}

	@Override
	public boolean supportsProvider(String provider) {
		if (!StringUtils.hasText(provider)) {
			return false;
		}
		String normalized = provider.trim().toLowerCase(Locale.ROOT);
		return PROVIDER_NAME.equals(normalized) || "okx_ws".equals(normalized);
	}

	@Override
	public MarketDataProviderDescriptor descriptor() {
		return new MarketDataProviderDescriptor(
			PROVIDER_NAME,
			MarketDataProviderStatus.UP,
			CAPABILITIES,
			"OKX public market data provider"
		);
	}

	@Override
	public Optional<PriceQuote> getQuote(PriceQuery query) {
		return okxPublicMarketDataClient.getQuote(query);
	}

	@Override
	public Optional<PriceOrderBook> getOrderBook(String instId, int depth) {
		return okxPublicMarketDataClient.getOrderBook(instId, depth);
	}

	@Override
	public List<PricePublicTrade> getRecentPublicTrades(String instId, int limit) {
		return okxPublicMarketDataClient.getRecentPublicTrades(instId, limit);
	}

	@Override
	public List<PriceHistoryCandle> getHistoryCandles(String instId, String bar, Long afterTs, int limit) {
		return okxPublicMarketDataClient.getHistoryCandles(instId, bar, afterTs, limit);
	}
}
