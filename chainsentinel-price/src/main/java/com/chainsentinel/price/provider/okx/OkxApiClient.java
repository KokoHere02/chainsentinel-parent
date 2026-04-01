package com.chainsentinel.price.provider.okx;

import com.chainsentinel.price.config.PriceProperties;
import com.chainsentinel.price.provider.okx.dto.OkxTickerResponse;
import java.net.URI;
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

  private final PriceProperties priceProperties;
  private final RestTemplateBuilder restTemplateBuilder;

  public OkxApiClient(PriceProperties priceProperties, RestTemplateBuilder restTemplateBuilder) {
    this.priceProperties = priceProperties;
    this.restTemplateBuilder = restTemplateBuilder;
  }

  public Optional<OkxTickerResponse> fetchTicker(String instId) {
    try {
      RestTemplate restTemplate = restTemplateBuilder
        .setConnectTimeout(java.time.Duration.ofMillis(priceProperties.getOkx().getTimeoutMs()))
        .setReadTimeout(java.time.Duration.ofMillis(priceProperties.getOkx().getTimeoutMs()))
        .build();
      URI uri = UriComponentsBuilder
        .fromHttpUrl(priceProperties.getOkx().getBaseUrl())
        .path("/api/v5/market/ticker")
        .queryParam("instId", instId)
        .build(true)
        .toUri();
      ResponseEntity<OkxTickerResponse> response = restTemplate.getForEntity(uri, OkxTickerResponse.class);
      return Optional.ofNullable(response.getBody());
    } catch (RestClientException ex) {
      log.warn("price.fetch.failed provider=okx instId={} error={}", instId, ex.getMessage());
      return Optional.empty();
    }
  }
}
