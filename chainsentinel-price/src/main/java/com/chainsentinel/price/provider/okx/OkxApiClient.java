package com.chainsentinel.price.provider.okx;

import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import com.chainsentinel.price.provider.okx.dto.OkxTickerResponse;
import java.net.URI;
import java.time.Duration;
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
}
