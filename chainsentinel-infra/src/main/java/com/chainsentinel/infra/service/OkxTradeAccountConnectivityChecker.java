package com.chainsentinel.infra.service;

import com.chainsentinel.infra.entity.TradeAccountEntity;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class OkxTradeAccountConnectivityChecker implements TradeAccountConnectivityChecker {

	private static final String OKX_BASE_URL = "https://www.okx.com";
	private static final String OKX_CONFIG_PATH = "/api/v5/account/config";

	private final RestTemplateBuilder restTemplateBuilder;

	public OkxTradeAccountConnectivityChecker(RestTemplateBuilder restTemplateBuilder) {
		this.restTemplateBuilder = restTemplateBuilder;
	}

	@Override
	public String provider() {
		return "OKX";
	}

	@Override
	public TradeConnectivityCheckResult test(TradeAccountEntity account, String apiSecret, String passphrase) {
		if (!StringUtils.hasText(account.getApiKey())) {
			return new TradeConnectivityCheckResult(false, "apiKey is required for OKX account");
		}
		if (!StringUtils.hasText(apiSecret)) {
			return new TradeConnectivityCheckResult(false, "apiSecret is required for OKX account");
		}
		if (!StringUtils.hasText(passphrase)) {
			return new TradeConnectivityCheckResult(false, "passphrase is required for OKX account");
		}
		try {
			RestTemplate restTemplate = restTemplateBuilder.build();
			String timestamp = Instant.now().toString();
			HttpHeaders headers = new HttpHeaders();
			headers.add("OK-ACCESS-KEY", account.getApiKey());
			headers.add("OK-ACCESS-PASSPHRASE", passphrase);
			headers.add("OK-ACCESS-TIMESTAMP", timestamp);
			headers.add("OK-ACCESS-SIGN", sign(timestamp + "GET" + OKX_CONFIG_PATH, apiSecret));
			if ("SIMULATED".equalsIgnoreCase(account.getEnvType())) {
				headers.add("x-simulated-trading", "1");
			}
			ResponseEntity<String> response = restTemplate.exchange(
				OKX_BASE_URL + OKX_CONFIG_PATH,
				HttpMethod.GET,
				new HttpEntity<>(headers),
				String.class
			);
			String body = response.getBody();
			if (response.getStatusCode().is2xxSuccessful() && body != null && body.contains("\"code\":\"0\"")) {
				return new TradeConnectivityCheckResult(true, "OKX connectivity check passed");
			}
			return new TradeConnectivityCheckResult(false, summarizeResponse(body, response.getStatusCode().value()));
		} catch (IllegalStateException ex) {
			return new TradeConnectivityCheckResult(false, ex.getMessage());
		} catch (RestClientException ex) {
			return new TradeConnectivityCheckResult(false, "OKX connectivity check failed: " + sanitize(ex.getMessage()));
		}
	}

	private String sign(String payload, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to sign OKX request", ex);
		}
	}

	private String summarizeResponse(String body, int statusCode) {
		if (!StringUtils.hasText(body)) {
			return "OKX connectivity check failed with status " + statusCode;
		}
		String compact = sanitize(body).replaceAll("\\s+", " ").trim();
		if (compact.length() > 180) {
			compact = compact.substring(0, 180) + "...";
		}
		return "OKX connectivity check failed with status " + statusCode + ": " + compact;
	}

	private String sanitize(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\r", " ").replace("\n", " ");
	}
}
