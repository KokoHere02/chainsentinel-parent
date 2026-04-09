package com.chainsentinel.price.provider.okx.ws;

import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.stream.PriceStreamQuote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultOkxWsMessageParser implements OkxWsMessageParser {

	private static final Logger log = LoggerFactory.getLogger(DefaultOkxWsMessageParser.class);
	private static final String CHANNEL_TICKERS = "tickers";

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public Optional<PriceStreamQuote> parse(String payload) {
		if (payload == null || payload.isBlank()) {
			return Optional.empty();
		}
		try {
			JsonNode root = objectMapper.readTree(payload);
			JsonNode eventNode = root.get("event");
			if (eventNode != null && !eventNode.isNull()) {
				String event = eventNode.asText();
				String code = root.path("code").asText();
				String msg = root.path("msg").asText();
				if ("error".equalsIgnoreCase(event)) {
					log.warn("price.ws.okx.event.error code={} msg={}", code, msg);
				} else {
					log.info("price.ws.okx.event event={} code={} msg={}", event, code, msg);
				}
				return Optional.empty();
			}
			JsonNode arg = root.path("arg");
			String channel = arg.path("channel").asText();
			if (!CHANNEL_TICKERS.equalsIgnoreCase(channel)) {
				return Optional.empty();
			}
			JsonNode data = root.path("data");
			if (!data.isArray() || data.isEmpty()) {
				return Optional.empty();
			}
			JsonNode first = data.get(0);
			String instId = first.path("instId").asText();
			String instTypeRaw = first.path("instType").asText();
			String last = first.path("last").asText();
			String ts = first.path("ts").asText();
			if (instId == null || instId.isBlank() || last == null || last.isBlank() || ts == null || ts.isBlank()) {
				return Optional.empty();
			}
			String[] parts = instId.split("-");
			if (parts.length < 2) {
				return Optional.empty();
			}
			PriceInstType instType;
			try {
				instType = PriceInstType.fromValue(instTypeRaw);
			} catch (Exception ex) {
				instType = PriceInstType.SPOT;
			}
			PriceStreamQuote quote = new PriceStreamQuote(
				"okx_ws",
				"OFFCHAIN",
				instType,
				parts[0],
				parts[1],
				new BigDecimal(last),
				Long.parseLong(ts)
			);
			return Optional.of(quote);
		} catch (Exception ex) {
			log.warn("price.ws.okx.parse.failed error={}", ex.getMessage());
			return Optional.empty();
		}
	}
}
