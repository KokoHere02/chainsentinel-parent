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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
	private final AtomicLong lastMessageAt = new AtomicLong(0L);
	private final AtomicBoolean firstQuoteLogged = new AtomicBoolean(false);
	private final AtomicLong lastPayloadLogAt = new AtomicLong(0L);
	private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
	private final AtomicBoolean stopping = new AtomicBoolean(false);
	private final List<PriceQuery> lastQueries = new CopyOnWriteArrayList<>();
	private volatile ScheduledExecutorService keepaliveExecutor;
	private volatile ScheduledExecutorService reconnectExecutor;
	private volatile String wsUrl;
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
		stopping.set(false);
		if (!started.compareAndSet(false, true)) {
			return;
		}
		this.wsUrl = runtimeConfig.providerBaseUrl(PROVIDER_NAME, DEFAULT_WS_URL);
		if (wsUrl == null || wsUrl.isBlank()) {
			log.warn("price.ws.okx.start.skip reason=blank_ws_url");
			return;
		}
		connectInternal();
	}

	private void connectInternal() {
		String url = this.wsUrl;
		if (url == null || url.isBlank()) {
			return;
		}
		client.connect(url, new WebSocketMessageHandler() {
			@Override
			public void onOpen() {
				reconnectScheduled.set(false);
				lastMessageAt.set(System.currentTimeMillis());
				log.info("price.ws.okx.connected url={}", url);
				startKeepalive();
				resubscribeLastQueriesIfAny();
			}

			@Override
			public void onText(String text) {
				long now = System.currentTimeMillis();
				lastMessageAt.set(now);
				if (text != null) {
					String trimmed = text.trim();
					if ("ping".equalsIgnoreCase(trimmed)) {
						client.sendText("pong");
						log.debug("price.ws.okx.pong.sent");
						return;
					}
					if ("pong".equalsIgnoreCase(trimmed)) {
						log.debug("price.ws.okx.pong.recv");
						return;
					}
				}
				logPayloadSample(text);
				Optional<PriceStreamQuote> quote = messageParser.parse(text);
				quote.ifPresent(q -> {
					PriceStreamSink target = OkxWsPriceStreamProvider.this.sink;
					if (target != null) {
						target.onQuote(q);
					}
					if (firstQuoteLogged.compareAndSet(false, true)) {
						log.info("price.ws.okx.quote.first instId={} price={} ts={}", q.instId(), q.price(), q.ts());
					}
				});
			}

			@Override
			public void onClose(int statusCode, String reason) {
				stopKeepalive();
				log.warn("price.ws.okx.closed status={} reason={}", statusCode, reason);
				scheduleReconnect("closed");
			}

			@Override
			public void onError(Throwable error) {
				stopKeepalive();
				log.warn("price.ws.okx.error error={}", error == null ? "unknown" : error.getMessage());
				scheduleReconnect("error");
			}
		});
	}

	@Override
	public void subscribe(List<PriceQuery> queries) {
		replaceLastQueries(queries);
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
		stopping.set(true);
		stopKeepalive();
		stopReconnect();
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

	private void startKeepalive() {
		ScheduledExecutorService executor = keepaliveExecutor;
		if (executor != null && !executor.isShutdown()) {
			return;
		}
		ThreadFactory factory = runnable -> {
			Thread thread = new Thread(runnable);
			thread.setName("okx-ws-keepalive");
			thread.setDaemon(true);
			return thread;
		};
		executor = Executors.newSingleThreadScheduledExecutor(factory);
		keepaliveExecutor = executor;
		executor.scheduleAtFixedRate(this::sendKeepaliveIfIdle, 10, 10, TimeUnit.SECONDS);
	}

	private void stopKeepalive() {
		ScheduledExecutorService executor = keepaliveExecutor;
		if (executor != null) {
			executor.shutdownNow();
		}
		keepaliveExecutor = null;
	}

	private void sendKeepaliveIfIdle() {
		if (!client.isConnected()) {
			return;
		}
		long last = lastMessageAt.get();
		if (last <= 0L) {
			return;
		}
		long idleMs = System.currentTimeMillis() - last;
		if (idleMs < 20000L) {
			return;
		}
		client.sendText("ping");
		log.debug("price.ws.okx.ping idledMs={}", idleMs);
	}

	private void logPayloadSample(String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		long now = System.currentTimeMillis();
		long last = lastPayloadLogAt.get();
		if (last > 0L && now - last < 30000L) {
			return;
		}
		lastPayloadLogAt.set(now);
		String sample = text;
		if (sample.length() > 200) {
			sample = sample.substring(0, 200);
		}
		log.info("price.ws.okx.payload.sample text={}", sample);
	}

	private void replaceLastQueries(List<PriceQuery> queries) {
		lastQueries.clear();
		if (queries == null || queries.isEmpty()) {
			return;
		}
		lastQueries.addAll(queries);
	}

	private void resubscribeLastQueriesIfAny() {
		if (lastQueries.isEmpty()) {
			return;
		}
		subscribe(new ArrayList<>(lastQueries));
	}

	private void scheduleReconnect(String reason) {
		if (stopping.get() || !started.get()) {
			return;
		}
		if (!reconnectScheduled.compareAndSet(false, true)) {
			return;
		}
		ScheduledExecutorService executor = reconnectExecutor;
		if (executor == null || executor.isShutdown()) {
			executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable);
				thread.setName("okx-ws-reconnect");
				thread.setDaemon(true);
				return thread;
			});
			reconnectExecutor = executor;
		}
		log.warn("price.ws.okx.reconnect.scheduled reason={} delaySec=3", reason);
		executor.schedule(() -> {
			if (stopping.get() || !started.get() || client.isConnected()) {
				reconnectScheduled.set(false);
				return;
			}
			log.info("price.ws.okx.reconnect.start");
			connectInternal();
		}, 3, TimeUnit.SECONDS);
	}

	private void stopReconnect() {
		reconnectScheduled.set(false);
		ScheduledExecutorService executor = reconnectExecutor;
		if (executor != null) {
			executor.shutdownNow();
		}
		reconnectExecutor = null;
	}
}
