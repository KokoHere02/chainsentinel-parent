package com.chainsentinel.marketgateway.client;

import com.chainsentinel.marketgateway.config.MarketDataGatewayProperties;
import com.chainsentinel.price.api.PublicMarketDataClient;
import com.chainsentinel.price.api.dto.PriceHistoryCandle;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PriceOrderBookLevel;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(prefix = "chainsentinel.price.market-data.gateway", name = "enabled", havingValue = "true")
public class HttpMarketDataGatewayClient implements PublicMarketDataClient {

	private static final Logger log = LoggerFactory.getLogger(HttpMarketDataGatewayClient.class);

	private final MarketDataGatewayProperties properties;
	private final RestTemplate restTemplate;

	public HttpMarketDataGatewayClient(MarketDataGatewayProperties properties, RestTemplateBuilder restTemplateBuilder) {
		this.properties = properties;
		int timeoutMs = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 3000;
		this.restTemplate = restTemplateBuilder
			.setConnectTimeout(Duration.ofMillis(timeoutMs))
			.setReadTimeout(Duration.ofMillis(timeoutMs))
			.build();
	}

	HttpMarketDataGatewayClient(MarketDataGatewayProperties properties, RestTemplate restTemplate) {
		this.properties = properties;
		this.restTemplate = restTemplate;
	}

	@Override
	public String provider() {
		return normalizeProviderName(properties.getProviderName());
	}

	@Override
	public boolean supportsProvider(String provider) {
		if (!StringUtils.hasText(provider)) {
			return false;
		}
		String normalized = provider.trim().toLowerCase(Locale.ROOT);
		if (provider().equals(normalized)) {
			return true;
		}
		for (String alias : properties.getAliases()) {
			if (StringUtils.hasText(alias) && normalized.equals(alias.trim().toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Optional<PriceQuote> getQuote(PriceQuery query) {
		if (query == null) {
			return Optional.empty();
		}
		try {
			ResponseEntity<GatewayQuoteResponse> response = getForEntity(
				UriComponentsBuilder.fromHttpUrl(baseUrl())
					.path("/api/v1/market/quotes/latest")
					.queryParam("provider", provider())
					.queryParam("instType", query.normalizedInstType())
					.queryParam("symbol", query.symbol())
					.queryParam("quoteSymbol", query.quoteSymbol())
					.queryParam("instId", query.normalizedInstId())
					.build(true)
					.toUri(),
				GatewayQuoteResponse.class);
			GatewayQuoteResponse body = response.getBody();
			if (body == null || body.price() == null || body.ts() == null) {
				return Optional.empty();
			}
			return Optional.of(new PriceQuote(
				fallback(body.baseSymbol(), query.symbol()).toUpperCase(Locale.ROOT),
				fallback(body.quoteSymbol(), query.quoteSymbol()).toUpperCase(Locale.ROOT),
				body.price(),
				body.ts(),
				fallback(body.provider(), provider()),
				body.stale() != null && body.stale()
			));
		} catch (RestClientException | IllegalArgumentException ex) {
			log.warn("market.gateway.quote.failed instId={} errorType={} error={}",
				query.normalizedInstId(), ex.getClass().getSimpleName(), ex.getMessage());
			return Optional.empty();
		}
	}

	@Override
	public Optional<PriceOrderBook> getOrderBook(String instId, int depth) {
		try {
			ResponseEntity<GatewayOrderBookResponse> response = getForEntity(
				UriComponentsBuilder.fromHttpUrl(baseUrl())
					.path("/api/v1/market/order-book")
					.queryParam("provider", provider())
					.queryParam("instId", instId)
					.queryParam("depth", depth)
					.build(true)
					.toUri(),
				GatewayOrderBookResponse.class);
			GatewayOrderBookResponse body = response.getBody();
			if (body == null) {
				return Optional.empty();
			}
			return Optional.of(new PriceOrderBook(
				fallback(body.provider(), provider()),
				fallback(body.instId(), instId),
				body.ts(),
				body.seqId(),
				body.checksum(),
				body.asks() == null ? List.of() : body.asks(),
				body.bids() == null ? List.of() : body.bids()
			));
		} catch (RestClientException | IllegalArgumentException ex) {
			log.warn("market.gateway.order_book.failed instId={} depth={} errorType={} error={}",
				instId, depth, ex.getClass().getSimpleName(), ex.getMessage());
			return Optional.empty();
		}
	}

	@Override
	public List<PricePublicTrade> getRecentPublicTrades(String instId, int limit) {
		try {
			ResponseEntity<GatewayTradesResponse> response = getForEntity(
				UriComponentsBuilder.fromHttpUrl(baseUrl())
					.path("/api/v1/market/trades/recent")
					.queryParam("provider", provider())
					.queryParam("instId", instId)
					.queryParam("limit", limit)
					.build(true)
					.toUri(),
				GatewayTradesResponse.class);
			GatewayTradesResponse body = response.getBody();
			return body == null || body.data() == null ? List.of() : body.data();
		} catch (RestClientException | IllegalArgumentException ex) {
			log.warn("market.gateway.trades.failed instId={} limit={} errorType={} error={}",
				instId, limit, ex.getClass().getSimpleName(), ex.getMessage());
			return List.of();
		}
	}

	@Override
	public List<PriceHistoryCandle> getHistoryCandles(String instId, String bar, Long afterTs, int limit) {
		try {
			UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl())
				.path("/api/v1/market/klines")
				.queryParam("provider", provider())
				.queryParam("instId", instId)
				.queryParam("bar", bar)
				.queryParam("limit", limit);
			if (afterTs != null) {
				builder.queryParam("after", afterTs);
			}
			ResponseEntity<GatewayCandlesResponse> response = getForEntity(
				builder.build(true).toUri(),
				GatewayCandlesResponse.class);
			GatewayCandlesResponse body = response.getBody();
			return body == null || body.data() == null ? List.of() : body.data();
		} catch (RestClientException | IllegalArgumentException ex) {
			log.warn("market.gateway.klines.failed instId={} bar={} after={} limit={} errorType={} error={}",
				instId, bar, afterTs, limit, ex.getClass().getSimpleName(), ex.getMessage());
			return List.of();
		}
	}

	private String baseUrl() {
		String baseUrl = properties.getBaseUrl();
		if (!StringUtils.hasText(baseUrl)) {
			return "http://localhost:18080";
		}
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	private <T> ResponseEntity<T> getForEntity(URI uri, Class<T> responseType) {
		String internalToken = properties.getInternalToken();
		if (!StringUtils.hasText(internalToken)) {
			return restTemplate.getForEntity(uri, responseType);
		}
		HttpHeaders headers = new HttpHeaders();
		headers.set(resolveInternalTokenHeaderName(), internalToken);
		return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), responseType);
	}

	private String resolveInternalTokenHeaderName() {
		String headerName = properties.getInternalTokenHeaderName();
		return StringUtils.hasText(headerName) ? headerName.trim() : "X-Internal-Token";
	}

	private String normalizeProviderName(String providerName) {
		return StringUtils.hasText(providerName) ? providerName.trim().toLowerCase(Locale.ROOT) : "market_gateway";
	}

	private String fallback(String value, String fallback) {
		return StringUtils.hasText(value) ? value : fallback;
	}

	public record GatewayQuoteResponse(
		String provider,
		String baseSymbol,
		String quoteSymbol,
		BigDecimal price,
		Long ts,
		Boolean stale
	) {
	}

	public record GatewayOrderBookResponse(
		String provider,
		String instId,
		Long ts,
		Long seqId,
		Long checksum,
		List<PriceOrderBookLevel> asks,
		List<PriceOrderBookLevel> bids
	) {
	}

	public record GatewayTradesResponse(
		List<PricePublicTrade> data
	) {
		public GatewayTradesResponse {
			data = data == null ? List.of() : new ArrayList<>(data);
		}
	}

	public record GatewayCandlesResponse(
		List<PriceHistoryCandle> data
	) {
		public GatewayCandlesResponse {
			data = data == null ? List.of() : new ArrayList<>(data);
		}
	}
}
