package com.chainsentinel.price.provider.okx;

import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import com.chainsentinel.price.provider.okx.dto.OkxHistoryCandle;
import com.chainsentinel.price.provider.okx.dto.OkxTickerResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OkxApiClient {

	private static final Logger log = LoggerFactory.getLogger(OkxApiClient.class);
	private static final String PROVIDER_NAME = "okx";
	private static final String DEFAULT_BASE_URL = "https://www.okx.com";
	private static final int DEFAULT_TIMEOUT_MS = 1500;

	private final PriceProviderRuntimeConfig runtimeConfig;
	private final RestTemplateBuilder restTemplateBuilder;

	public OkxApiClient(PriceProviderRuntimeConfig runtimeConfig, RestTemplateBuilder restTemplateBuilder) {
		this.runtimeConfig = runtimeConfig;
		this.restTemplateBuilder = restTemplateBuilder;
	}

	public Optional<OkxTickerResponse> fetchTicker(String instId) {
		String baseUrl = runtimeConfig.providerBaseUrl(PROVIDER_NAME, DEFAULT_BASE_URL);
		int timeoutMs = runtimeConfig.providerTimeoutMs(PROVIDER_NAME, DEFAULT_TIMEOUT_MS);
		try {
			RestTemplate restTemplate = restTemplateBuilder
				.setConnectTimeout(Duration.ofMillis(timeoutMs))
				.setReadTimeout(Duration.ofMillis(timeoutMs))
				.build();
			URI uri = UriComponentsBuilder
				.fromHttpUrl(baseUrl)
				.path("/api/v5/market/ticker")
				.queryParam("instId", instId)
				.build(true)
				.toUri();
			ResponseEntity<OkxTickerResponse> response = restTemplate.getForEntity(uri, OkxTickerResponse.class);
			return Optional.ofNullable(response.getBody());
		} catch (RestClientException | IllegalArgumentException ex) {
			log.warn("price.fetch.failed provider=okx instId={} baseUrl={} error={}", instId, baseUrl, ex.getMessage());
			return Optional.empty();
		}
	}

	public List<OkxHistoryCandle> fetchHistoryCandles(String instId, String bar, Long afterTs, int limit) {
		String baseUrl = runtimeConfig.providerBaseUrl(PROVIDER_NAME, DEFAULT_BASE_URL);
		int timeoutMs = runtimeConfig.providerTimeoutMs(PROVIDER_NAME, DEFAULT_TIMEOUT_MS);
		int safeLimit = Math.max(1, Math.min(300, limit));
		String safeBar = (bar == null || bar.isBlank()) ? "1m" : bar.trim();
		try {
			RestTemplate restTemplate = restTemplateBuilder
				.setConnectTimeout(Duration.ofMillis(timeoutMs))
				.setReadTimeout(Duration.ofMillis(timeoutMs))
				.build();
			UriComponentsBuilder builder = UriComponentsBuilder
				.fromHttpUrl(baseUrl)
				.path("/api/v5/market/history-candles")
				.queryParam("instId", instId)
				.queryParam("bar", safeBar)
				.queryParam("limit", safeLimit);
			if (afterTs != null && afterTs > 0) {
				builder.queryParam("after", afterTs);
			}
			URI uri = builder.build(true).toUri();
			ResponseEntity<JsonNode> response = restTemplate.getForEntity(uri, JsonNode.class);
			JsonNode body = response.getBody();
			if (body == null) {
				return List.of();
			}
			String code = body.path("code").asText();
			if (!"0".equals(code)) {
				log.warn("price.okx.history.failed instId={} bar={} after={} code={} msg={}",
					instId, safeBar, afterTs, code, body.path("msg").asText());
				return List.of();
			}
			JsonNode data = body.path("data");
			if (!data.isArray()) {
				return List.of();
			}
			List<OkxHistoryCandle> candles = new ArrayList<>();
			for (JsonNode row : data) {
				if (!row.isArray() || row.size() < 5) {
					continue;
				}
				try {
					long ts = Long.parseLong(row.get(0).asText());
					BigDecimal close = new BigDecimal(row.get(4).asText());
					candles.add(new OkxHistoryCandle(ts, close));
				} catch (Exception ignore) {
					// ignore malformed rows
				}
			}
			return candles;
		} catch (RestClientException | IllegalArgumentException ex) {
			log.warn("price.okx.history.exception instId={} bar={} after={} error={}",
				instId, safeBar, afterTs, ex.getMessage());
			return List.of();
		}
	}
}