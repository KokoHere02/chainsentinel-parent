package com.chainsentinel.price.provider.okx;

import com.chainsentinel.price.api.PublicMarketDataClient;
import com.chainsentinel.price.api.dto.PriceHistoryCandle;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import com.chainsentinel.price.provider.okx.dto.OkxOrderBookResponse;
import com.chainsentinel.price.provider.okx.dto.OkxTickerResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OkxPublicMarketDataClient implements PublicMarketDataClient {

	private static final String PROVIDER_NAME = "okx";

	private final OkxApiClient okxApiClient;

	public OkxPublicMarketDataClient(OkxApiClient okxApiClient) {
		this.okxApiClient = okxApiClient;
	}

	@Override
	public String provider() {
		return PROVIDER_NAME;
	}

	@Override
	public boolean supportsProvider(String provider) {
		if (provider == null || provider.isBlank()) {
			return true;
		}
		String normalized = provider.trim().toLowerCase(Locale.ROOT);
		return PROVIDER_NAME.equals(normalized) || "okx_ws".equals(normalized);
	}

	@Override
	public Optional<PriceQuote> getQuote(PriceQuery query) {
		if (query == null) {
			return Optional.empty();
		}
		Optional<OkxTickerResponse> responseOpt = okxApiClient.fetchTicker(query.normalizedInstId());
		if (responseOpt.isEmpty()) {
			return Optional.empty();
		}
		OkxTickerResponse response = responseOpt.get();
		if (!"0".equals(response.getCode()) || response.getData() == null || response.getData().isEmpty()) {
			return Optional.empty();
		}
		OkxTickerResponse.OkxTickerData first = response.getData().get(0);
		return Optional.of(new PriceQuote(
			query.symbol().toUpperCase(Locale.ROOT),
			query.quoteSymbol().toUpperCase(Locale.ROOT),
			new BigDecimal(first.getLast()),
			Long.parseLong(first.getTs()),
			PROVIDER_NAME,
			false
		));
	}

	@Override
	public Optional<PriceOrderBook> getOrderBook(String instId, int depth) {
		return okxApiClient.fetchOrderBook(instId, depth).map(this::toOrderBook);
	}

	@Override
	public List<PricePublicTrade> getRecentPublicTrades(String instId, int limit) {
		return okxApiClient.fetchRecentPublicTrades(instId, limit);
	}

	@Override
	public List<PriceHistoryCandle> getHistoryCandles(String instId, String bar, Long afterTs, int limit) {
		return okxApiClient.fetchHistoryCandles(instId, bar, afterTs, limit)
			.stream()
			.map(candle -> new PriceHistoryCandle(candle.ts(), candle.closePrice()))
			.toList();
	}

	private PriceOrderBook toOrderBook(OkxOrderBookResponse response) {
		return new PriceOrderBook(
			PROVIDER_NAME,
			response.instId(),
			response.ts(),
			response.seqId(),
			response.checksum(),
			response.asks(),
			response.bids()
		);
	}
}
