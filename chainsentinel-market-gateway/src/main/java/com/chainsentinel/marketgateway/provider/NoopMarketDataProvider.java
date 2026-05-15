package com.chainsentinel.marketgateway.provider;

import com.chainsentinel.price.api.dto.PriceHistoryCandle;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "chainsentinel.market-gateway.noop-provider", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NoopMarketDataProvider implements MarketDataProvider {

	private static final String PROVIDER_NAME = "noop";
	private static final List<MarketDataCapability> CAPABILITIES = List.of(
		MarketDataCapability.QUOTE,
		MarketDataCapability.ORDER_BOOK,
		MarketDataCapability.TRADES,
		MarketDataCapability.KLINES
	);

	@Override
	public String provider() {
		return PROVIDER_NAME;
	}

	@Override
	public boolean supportsProvider(String provider) {
		if (!StringUtils.hasText(provider)) {
			return true;
		}
		return PROVIDER_NAME.equals(provider.trim().toLowerCase(Locale.ROOT));
	}

	@Override
	public MarketDataProviderDescriptor descriptor() {
		return new MarketDataProviderDescriptor(
			PROVIDER_NAME,
			MarketDataProviderStatus.DEGRADED,
			CAPABILITIES,
			"noop provider is enabled for contract verification only"
		);
	}

	@Override
	public Optional<PriceQuote> getQuote(PriceQuery query) {
		return Optional.empty();
	}

	@Override
	public Optional<PriceOrderBook> getOrderBook(String instId, int depth) {
		return Optional.empty();
	}

	@Override
	public List<PricePublicTrade> getRecentPublicTrades(String instId, int limit) {
		return List.of();
	}

	@Override
	public List<PriceHistoryCandle> getHistoryCandles(String instId, String bar, Long afterTs, int limit) {
		return List.of();
	}
}
