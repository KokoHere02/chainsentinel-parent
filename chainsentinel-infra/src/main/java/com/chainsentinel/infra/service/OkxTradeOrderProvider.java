package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.dto.TradeOrderCreateCommand;
import com.chainsentinel.infra.entity.TradeAccountEntity;
import com.chainsentinel.infra.entity.TradeOrderEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class OkxTradeOrderProvider implements TradeOrderProvider {

	private static final String OKX_BASE_URL = "https://www.okx.com";
	private static final String OKX_ORDER_PATH = "/api/v5/trade/order";
	private static final String OKX_CANCEL_PATH = "/api/v5/trade/cancel-order";
	private static final String OKX_ORDER_QUERY_PATH = "/api/v5/trade/order";
	private static final String OKX_FILLS_PATH = "/api/v5/trade/fills";

	private final RestTemplateBuilder restTemplateBuilder;
	private final ObjectMapper objectMapper;

	public OkxTradeOrderProvider(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
		this.restTemplateBuilder = restTemplateBuilder;
		this.objectMapper = objectMapper;
	}

	@Override
	public String provider() {
		return "OKX";
	}

	@Override
	public TradeProviderSubmitResult submit(
		TradeAccountEntity account,
		String apiSecret,
		String passphrase,
		TradeOrderCreateCommand command
	) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("instId", command.symbol());
		body.put("tdMode", "cash");
		body.put("side", command.side().toLowerCase());
		body.put("ordType", command.orderType().toLowerCase());
		body.put("sz", command.quantity().toPlainString());
		body.put("clOrdId", command.clientOrderId());
		if (command.price() != null) {
			body.put("px", command.price().toPlainString());
		}
		return exchange(account, apiSecret, passphrase, OKX_ORDER_PATH, body, "SUBMITTED");
	}

	@Override
	public TradeProviderCancelResult cancel(
		TradeAccountEntity account,
		String apiSecret,
		String passphrase,
		TradeOrderEntity order
	) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("instId", order.getSymbol());
		if (StringUtils.hasText(order.getProviderOrderId())) {
			body.put("ordId", order.getProviderOrderId());
		} else {
			body.put("clOrdId", order.getClientOrderId());
		}
		TradeProviderSubmitResult result = exchange(account, apiSecret, passphrase, OKX_CANCEL_PATH, body, "CANCELED");
		return new TradeProviderCancelResult(result.success(), result.status(), result.errorCode(), result.errorMessage());
	}

	@Override
	public TradeProviderOrderState queryOrder(
		TradeAccountEntity account,
		String apiSecret,
		String passphrase,
		TradeOrderEntity order
	) {
		try {
			String query = buildOrderQuery(order);
			String pathWithQuery = OKX_ORDER_QUERY_PATH + "?" + query;
			String timestamp = Instant.now().toString();
			HttpHeaders headers = buildGetHeaders(account, apiSecret, passphrase, timestamp, pathWithQuery);
			RestTemplate restTemplate = restTemplateBuilder.build();
			ResponseEntity<String> response = restTemplate.exchange(
				OKX_BASE_URL + pathWithQuery,
				HttpMethod.GET,
				new HttpEntity<>(headers),
				String.class
			);
			return parseOrderState(response.getBody());
		} catch (RestClientException ex) {
			return new TradeProviderOrderState(false, "FAILED", order.getProviderOrderId(), null, null, null, "OKX_HTTP_ERROR", sanitize(ex.getMessage()));
		} catch (Exception ex) {
			return new TradeProviderOrderState(false, "FAILED", order.getProviderOrderId(), null, null, null, "OKX_QUERY_ERROR", sanitize(ex.getMessage()));
		}
	}

	@Override
	public java.util.List<TradeProviderFillState> listFills(
		TradeAccountEntity account,
		String apiSecret,
		String passphrase,
		TradeOrderEntity order
	) {
		try {
			String query = buildOrderQuery(order);
			String pathWithQuery = OKX_FILLS_PATH + "?" + query;
			String timestamp = Instant.now().toString();
			HttpHeaders headers = buildGetHeaders(account, apiSecret, passphrase, timestamp, pathWithQuery);
			RestTemplate restTemplate = restTemplateBuilder.build();
			ResponseEntity<String> response = restTemplate.exchange(
				OKX_BASE_URL + pathWithQuery,
				HttpMethod.GET,
				new HttpEntity<>(headers),
				String.class
			);
			return parseFillStates(response.getBody());
		} catch (Exception ex) {
			return java.util.List.of();
		}
	}

	private TradeProviderSubmitResult exchange(
		TradeAccountEntity account,
		String apiSecret,
		String passphrase,
		String path,
		Map<String, Object> body,
		String successStatus
	) {
		try {
			String bodyJson = objectMapper.writeValueAsString(body);
			String timestamp = Instant.now().toString();
			HttpHeaders headers = buildHeaders(account, apiSecret, passphrase, timestamp, path, bodyJson);
			RestTemplate restTemplate = restTemplateBuilder.build();
			ResponseEntity<String> response = restTemplate.exchange(
				OKX_BASE_URL + path,
				HttpMethod.POST,
				new HttpEntity<>(bodyJson, headers),
				String.class
			);
			return parseResponse(response.getBody(), successStatus);
		} catch (RestClientException ex) {
			return new TradeProviderSubmitResult(false, null, "FAILED", "OKX_HTTP_ERROR", sanitize(ex.getMessage()));
		} catch (Exception ex) {
			return new TradeProviderSubmitResult(false, null, "FAILED", "OKX_SIGN_ERROR", sanitize(ex.getMessage()));
		}
	}

	private HttpHeaders buildHeaders(
		TradeAccountEntity account,
		String apiSecret,
		String passphrase,
		String timestamp,
		String path,
		String bodyJson
	) throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.add("OK-ACCESS-KEY", account.getApiKey());
		headers.add("OK-ACCESS-PASSPHRASE", passphrase);
		headers.add("OK-ACCESS-TIMESTAMP", timestamp);
		headers.add("OK-ACCESS-SIGN", sign(timestamp + "POST" + path + bodyJson, apiSecret));
		headers.setContentType(MediaType.APPLICATION_JSON);
		if ("SIMULATED".equalsIgnoreCase(account.getEnvType())) {
			headers.add("x-simulated-trading", "1");
		}
		return headers;
	}

	private HttpHeaders buildGetHeaders(
		TradeAccountEntity account,
		String apiSecret,
		String passphrase,
		String timestamp,
		String pathWithQuery
	) throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.add("OK-ACCESS-KEY", account.getApiKey());
		headers.add("OK-ACCESS-PASSPHRASE", passphrase);
		headers.add("OK-ACCESS-TIMESTAMP", timestamp);
		headers.add("OK-ACCESS-SIGN", sign(timestamp + "GET" + pathWithQuery, apiSecret));
		if ("SIMULATED".equalsIgnoreCase(account.getEnvType())) {
			headers.add("x-simulated-trading", "1");
		}
		return headers;
	}

	private String sign(String payload, String secret) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
	}

	private TradeProviderSubmitResult parseResponse(String body, String successStatus) throws Exception {
		if (!StringUtils.hasText(body)) {
			return new TradeProviderSubmitResult(false, null, "FAILED", "OKX_EMPTY_RESPONSE", "empty response");
		}
		JsonNode root = objectMapper.readTree(body);
		String code = root.path("code").asText();
		String message = root.path("msg").asText();
		JsonNode first = root.path("data").isArray() && root.path("data").size() > 0 ? root.path("data").get(0) : null;
		if (!"0".equals(code)) {
			return new TradeProviderSubmitResult(false, null, "FAILED", code, sanitize(message));
		}
		String providerCode = first == null ? "" : first.path("sCode").asText("");
		String providerMsg = first == null ? "" : first.path("sMsg").asText("");
		if (StringUtils.hasText(providerCode) && !"0".equals(providerCode)) {
			return new TradeProviderSubmitResult(false, null, "REJECTED", providerCode, sanitize(providerMsg));
		}
		String providerOrderId = first == null ? null : first.path("ordId").asText(null);
		return new TradeProviderSubmitResult(true, providerOrderId, successStatus, null, null);
	}

	private TradeProviderOrderState parseOrderState(String body) throws Exception {
		if (!StringUtils.hasText(body)) {
			return new TradeProviderOrderState(false, "FAILED", null, null, null, null, "OKX_EMPTY_RESPONSE", "empty response");
		}
		JsonNode root = objectMapper.readTree(body);
		String code = root.path("code").asText();
		String message = root.path("msg").asText();
		JsonNode first = root.path("data").isArray() && root.path("data").size() > 0 ? root.path("data").get(0) : null;
		if (!"0".equals(code) || first == null) {
			return new TradeProviderOrderState(false, "FAILED", null, null, null, null, code, sanitize(message));
		}
		return new TradeProviderOrderState(
			true,
			mapOrderState(first.path("state").asText()),
			first.path("ordId").asText(null),
			toDecimal(first.path("avgPx").asText(null)),
			toDecimal(first.path("accFillSz").asText(null)),
			resolveFilledAmount(
				toDecimal(first.path("avgPx").asText(null)),
				toDecimal(first.path("accFillSz").asText(null))
			),
			null,
			null
		);
	}

	private java.util.List<TradeProviderFillState> parseFillStates(String body) throws Exception {
		if (!StringUtils.hasText(body)) {
			return java.util.List.of();
		}
		JsonNode root = objectMapper.readTree(body);
		if (!"0".equals(root.path("code").asText())) {
			return java.util.List.of();
		}
		java.util.List<TradeProviderFillState> results = new java.util.ArrayList<>();
		for (JsonNode item : root.path("data")) {
			results.add(new TradeProviderFillState(
				item.path("tradeId").asText(),
				item.path("instId").asText(),
				item.path("side").asText("").toUpperCase(),
				toDecimal(item.path("fillPx").asText(null)),
				toDecimal(item.path("fillSz").asText(null)),
				toDecimal(item.path("fillFee").asText(null)),
				item.path("fillFeeCcy").asText(null),
				toInstant(item.path("fillTime").asText(null))
			));
		}
		return results;
	}

	private String buildOrderQuery(TradeOrderEntity order) {
		StringBuilder builder = new StringBuilder();
		builder.append("instId=").append(order.getSymbol());
		if (StringUtils.hasText(order.getProviderOrderId())) {
			builder.append("&ordId=").append(order.getProviderOrderId());
		} else {
			builder.append("&clOrdId=").append(order.getClientOrderId());
		}
		return builder.toString();
	}

	private String mapOrderState(String state) {
		return switch (state) {
			case "live" -> "SUBMITTED";
			case "partially_filled" -> "PARTIALLY_FILLED";
			case "filled" -> "FILLED";
			case "canceled", "mmp_canceled" -> "CANCELED";
			default -> "FAILED";
		};
	}

	private java.math.BigDecimal resolveFilledAmount(java.math.BigDecimal avgFillPrice, java.math.BigDecimal filledQuantity) {
		if (avgFillPrice == null || filledQuantity == null) {
			return java.math.BigDecimal.ZERO;
		}
		return avgFillPrice.multiply(filledQuantity);
	}

	private java.math.BigDecimal toDecimal(String text) {
		if (!StringUtils.hasText(text)) {
			return null;
		}
		return new java.math.BigDecimal(text);
	}

	private Instant toInstant(String millisText) {
		if (!StringUtils.hasText(millisText)) {
			return null;
		}
		return Instant.ofEpochMilli(Long.parseLong(millisText));
	}

	private String sanitize(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\r", " ").replace("\n", " ");
	}
}
