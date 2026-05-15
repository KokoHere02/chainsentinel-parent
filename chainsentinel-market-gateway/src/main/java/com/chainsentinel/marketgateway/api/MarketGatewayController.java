package com.chainsentinel.marketgateway.api;

import com.chainsentinel.marketgateway.provider.MarketDataProvider;
import com.chainsentinel.marketgateway.provider.MarketDataProviderDescriptor;
import com.chainsentinel.marketgateway.provider.MarketDataProviderRouter;
import com.chainsentinel.marketgateway.provider.MarketDataProviderStatus;
import com.chainsentinel.price.api.dto.PriceHistoryCandle;
import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market")
public class MarketGatewayController {

	private final MarketDataProviderRouter providerRouter;

	public MarketGatewayController(MarketDataProviderRouter providerRouter) {
		this.providerRouter = providerRouter;
	}

	@GetMapping("/health")
	public HealthResponse health() {
		List<MarketDataProviderDescriptor> providers = providerRouter.descriptors();
		MarketDataProviderStatus status = providers.stream().anyMatch(provider -> provider.status() == MarketDataProviderStatus.UP)
			? MarketDataProviderStatus.UP
			: MarketDataProviderStatus.DEGRADED;
		return new HealthResponse(status, providers, Instant.now().toEpochMilli());
	}

	@GetMapping("/providers")
	public ProvidersResponse providers() {
		return new ProvidersResponse(providerRouter.descriptors());
	}

	@GetMapping("/quotes/latest")
	public PriceQuote latestQuote(
		@RequestParam(required = false) String provider,
		@RequestParam(required = false) PriceInstType instType,
		@RequestParam @NotBlank String symbol,
		@RequestParam @NotBlank String quoteSymbol
	) {
		MarketDataProvider resolved = providerRouter.resolve(provider);
		return resolved.getQuote(new PriceQuery(null, instType, symbol, quoteSymbol, null))
			.orElseThrow(() -> MarketGatewayException.notFound("quote not available"));
	}

	@GetMapping("/order-book")
	public PriceOrderBook orderBook(
		@RequestParam(required = false) String provider,
		@RequestParam @NotBlank String instId,
		@RequestParam(defaultValue = "20") @Min(1) @Max(400) int depth
	) {
		MarketDataProvider resolved = providerRouter.resolve(provider);
		return resolved.getOrderBook(instId, depth)
			.orElseThrow(() -> MarketGatewayException.notFound("order book not available"));
	}

	@GetMapping("/trades/recent")
	public TradesResponse recentTrades(
		@RequestParam(required = false) String provider,
		@RequestParam @NotBlank String instId,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
	) {
		MarketDataProvider resolved = providerRouter.resolve(provider);
		return new TradesResponse(resolved.getRecentPublicTrades(instId, limit));
	}

	@GetMapping("/klines")
	public CandlesResponse klines(
		@RequestParam(required = false) String provider,
		@RequestParam @NotBlank String instId,
		@RequestParam(defaultValue = "1m") String bar,
		@RequestParam(required = false) Long after,
		@RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit
	) {
		MarketDataProvider resolved = providerRouter.resolve(provider);
		return new CandlesResponse(resolved.getHistoryCandles(instId, bar, after, limit));
	}

	public record TradesResponse(List<PricePublicTrade> data) {
	}

	public record CandlesResponse(List<PriceHistoryCandle> data) {
	}

	public record HealthResponse(
		MarketDataProviderStatus status,
		List<MarketDataProviderDescriptor> providers,
		long timestamp
	) {
	}

	public record ProvidersResponse(List<MarketDataProviderDescriptor> providers) {
	}
}
