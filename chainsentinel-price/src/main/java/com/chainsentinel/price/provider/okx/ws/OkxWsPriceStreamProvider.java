package com.chainsentinel.price.provider.okx.ws;

import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import com.chainsentinel.price.stream.PriceStreamProvider;
import com.chainsentinel.price.stream.PriceStreamQuote;
import com.chainsentinel.price.stream.PriceStreamSink;
import com.chainsentinel.price.stream.ws.SimpleWebSocketClient;
import com.chainsentinel.price.stream.ws.WebSocketMessageHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OkxWsPriceStreamProvider implements PriceStreamProvider {

	private static final Logger log = LoggerFactory.getLogger(OkxWsPriceStreamProvider.class);
	private static final String PROVIDER_NAME = "okx_ws";
	private static final String DEFAULT_WS_URL = "wss://ws.okx.com:8443/ws/v5/public";

	private final PriceProviderRuntimeConfig runtimeConfig;
	private final OkxWsMessageParser messageParser;
	private final SimpleWebSocketClient client;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final AtomicBoolean started = new AtomicBoolean(false);
	private volatile PriceStreamSink sink;

	public OkxWsPriceStreamProvider(PriceProviderRuntimeConfig runtimeConfig, OkxWsMessageParser messageParser) {
		this.runtimeConfig = runtimeConfig;
		this.messageParser = messageParser;
		this.client = new SimpleWebSocketClient(Duration.ofSeconds(10));
	}

	@Override
	public String name() {
		return PROVIDER_NAME;
	}

	@Override
	public boolean enabled() {
		return runtimeConfig.providerEnabled(PROVIDER_NAME) || runtimeConfig.providerEnabled("okx");
	}

	@Override
	public boolean supports(PriceQuery query) {
		return query != null && query.instType() == PriceInstType.SPOT;
	}

	@Override
	public void start(PriceStreamSink sink) {
		this.sink = sink;
		if (!started.compareAndSet(false, true)) {
			return;
		}
		String wsUrl = runtimeConfig.providerBaseUrl(PROVIDER_NAME, DEFAULT_WS_URL);
		if (wsUrl == null || wsUrl.isBlank()) {
			log.warn("price.ws.okx.start.skip reason=blank_ws_url");
			return;
		}
		client.connect(wsUrl, new WebSocketMessageHandler() {
			@Override
			public void onOpen() {
				log.info("price.ws.okx.connected url={}", wsUrl);
			}

			@Override
			public void onText(String text) {
				Optional<PriceStreamQuote> quote = messageParser.parse(text);
				quote.ifPresent(q -> {
					PriceStreamSink target = OkxWsPriceStreamProvider.this.sink;
					if (target != null) {
						target.onQuote(q);
					}
				});
			}

			@Override
			public void onClose(int statusCode, String reason) {
				log.warn("price.ws.okx.closed status={} reason={}", statusCode, reason);
			}

			@Override
			public void onError(Throwable error) {
				log.warn("price.ws.okx.error error={}", error == null ? "unknown" : error.getMessage());
			}
		});
	}

	@Override
	public void subscribe(List<PriceQuery> queries) {
		if (!client.isConnected()) {
			log.warn("price.ws.okx.subscribe.skip reason=not_connected");
			return;
		}
		if (queries == null || queries.isEmpty()) {
			return;
		}
		List<String> instIds = new ArrayList<>();
		for (PriceQuery query : queries) {
			String instId = buildInstId(query);
			if (instId != null && !instIds.contains(instId)) {
				instIds.add(instId);
			}
		}
		if (instIds.isEmpty()) {
			return;
		}
		String payload = buildSubscribePayload(instIds);
		client.sendText(payload);
		log.info("price.ws.okx.subscribe.sent count={} instIds={}", instIds.size(), instIds);
	}

	@Override
	public void stop() {
		client.close();
		started.set(false);
		log.info("price.ws.okx.stopped");
	}

	private String buildInstId(PriceQuery query) {
		if (query == null || query.symbol() == null || query.quoteSymbol() == null) {
			return null;
		}
		return (query.symbol().trim() + "-" + query.quoteSymbol().trim()).toUpperCase(Locale.ROOT);
	}

	private String buildSubscribePayload(List<String> instIds) {
		if (instIds == null || instIds.isEmpty()) {
			return "";
		}
		List<Map<String, Object>> args = new ArrayList<>();
		for (String instId : instIds) {
			Map<String, Object> arg = new HashMap<>();
			arg.put("channel", "tickers");
			arg.put("instId", instId);
			args.add(arg);
		}
		Map<String, Object> payload = new HashMap<>();
		payload.put("op", "subscribe");
		payload.put("args", args);
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (Exception ex) {
			log.warn("price.ws.okx.subscribe.payload_failed count={} error={}", instIds.size(), ex.getMessage());
			return "";
		}
	}
}