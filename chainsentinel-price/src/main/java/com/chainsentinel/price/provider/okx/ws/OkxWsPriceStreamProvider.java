package com.chainsentinel.price.provider.okx.ws;

import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import com.chainsentinel.price.stream.PriceStreamProvider;
import com.chainsentinel.price.stream.PriceStreamProviderStatus;
import com.chainsentinel.price.stream.PriceStreamQuote;
import com.chainsentinel.price.stream.PriceStreamSink;
import com.chainsentinel.price.stream.PriceStreamStatusAware;
import com.chainsentinel.price.stream.ws.SimpleWebSocketClient;
import com.chainsentinel.price.stream.ws.WebSocketMessageHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OkxWsPriceStreamProvider implements PriceStreamProvider, PriceStreamStatusAware {

	private static final Logger log = LoggerFactory.getLogger(OkxWsPriceStreamProvider.class);
	private static final String PROVIDER_NAME = "okx_ws";
	private static final String DEFAULT_WS_URL = "wss://ws.okx.com:8443/ws/v5/public";
	private static final String METRIC_WS_MESSAGE_TOTAL = "price_ws_message_total";
	private static final String METRIC_WS_SUBSCRIBE_TOTAL = "price_ws_subscribe_total";
	private static final String METRIC_WS_RECONNECT_TOTAL = "price_ws_reconnect_total";
	private static final String METRIC_WS_KEEPALIVE_TOTAL = "price_ws_keepalive_total";
	private static final String METRIC_WS_DISCONNECT_TOTAL = "price_ws_disconnect_total";
	private static final String METRIC_WS_RECOVERY_DURATION = "price_ws_recovery_duration";
	private static final String METRIC_WS_FIRST_QUOTE_TIMEOUT_TOTAL = "price_ws_first_quote_timeout_total";
	private static final String METRIC_WS_QUOTE_DROPPED_TOTAL = "price_ws_quote_dropped_total";
	private static final long DEFAULT_QUOTE_SHORT_WINDOW_MS = 2000L;
	private static final double DEFAULT_QUOTE_MAX_JUMP_RATIO = 0.20D;
	private static final long DEFAULT_DROP_LOG_INTERVAL_MS = 30000L;
	private static final int QUOTE_JUMP_RATIO_SCALE = 8;
	private static final BigDecimal DECIMAL_ZERO = BigDecimal.ZERO;
	private static final long FIRST_QUOTE_TIMEOUT_MS = 30000L;
	private static final int RECONNECT_BASE_DELAY_SEC = 3;
	private static final int RECONNECT_MAX_DELAY_SEC = 30;

	private final PriceProviderRuntimeConfig runtimeConfig;
	private final OkxWsMessageParser messageParser;
	private final SimpleWebSocketClient client;
	private final MeterRegistry meterRegistry;
	private final OkxWsQuoteGuardProperties quoteGuardProperties;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final AtomicBoolean started = new AtomicBoolean(false);
	private final AtomicLong lastMessageAt = new AtomicLong(0L);
	private final AtomicBoolean firstQuoteLogged = new AtomicBoolean(false);
	private final AtomicLong lastPayloadLogAt = new AtomicLong(0L);
	private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
	private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
	private final AtomicLong lastReconnectAtMs = new AtomicLong(0L);
	private final AtomicLong lastErrorAtMs = new AtomicLong(0L);
	private final AtomicLong lastResubscribeAtMs = new AtomicLong(0L);
	private final AtomicInteger lastResubscribeCount = new AtomicInteger(0);
	private final AtomicLong lastDisconnectedAtMs = new AtomicLong(0L);
	private volatile String lastReconnectReason;
	private volatile String lastErrorType;
	private volatile String lastErrorMessage;
	private final AtomicBoolean stopping = new AtomicBoolean(false);
	private final List<PriceQuery> lastQueries = new CopyOnWriteArrayList<>();
	private final Map<String, Long> pendingFirstQuoteAt = new ConcurrentHashMap<>();
	private final Map<String, Long> lastQuoteTsByInst = new ConcurrentHashMap<>();
	private final Map<String, BigDecimal> lastPriceByInst = new ConcurrentHashMap<>();
	private final Map<String, Long> lastQuoteDropLogAtByKey = new ConcurrentHashMap<>();
	private volatile ScheduledExecutorService keepaliveExecutor;
	private volatile ScheduledExecutorService reconnectExecutor;
	private volatile String wsUrl;
	private volatile PriceStreamSink sink;

	public OkxWsPriceStreamProvider(
		PriceProviderRuntimeConfig runtimeConfig,
		OkxWsMessageParser messageParser,
		MeterRegistry meterRegistry,
		OkxWsQuoteGuardProperties quoteGuardProperties
	) {
		this.runtimeConfig = runtimeConfig;
		this.messageParser = messageParser;
		this.meterRegistry = meterRegistry;
		this.quoteGuardProperties = quoteGuardProperties;
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
		if (query == null || query.instType() == null) {
			return false;
		}
		return switch (query.instType()) {
			case SPOT, MARGIN, SWAP, FUTURES, OPTION -> true;
		};
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
				reconnectAttempts.set(0);
				lastMessageAt.set(System.currentTimeMillis());
				recordRecoveryDurationIfNeeded();
				log.info("price.ws.okx.connected url={}", url);
				incrementMessageCounter("connected");
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
						incrementKeepaliveCounter("pong_send");
						return;
					}
					if ("pong".equalsIgnoreCase(trimmed)) {
						log.debug("price.ws.okx.pong.recv");
						incrementKeepaliveCounter("pong_recv");
						return;
					}
				}
				logPayloadSample(text);
				if (handleControlEventMessage(text)) {
					return;
				}
				incrementMessageCounter("payload");
				Optional<PriceStreamQuote> quote = messageParser.parse(text);
				if (quote.isEmpty()) {
					incrementMessageCounter("non_quote");
				}
				quote.ifPresent(q -> {
					if (!acceptQuote(q)) {
						incrementMessageCounter("quote_dropped");
						return;
					}
					incrementMessageCounter("quote");
					markFirstQuoteReceived(q.instId(), q.ts());
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
				markDisconnected("closed");
				log.warn("price.ws.okx.closed status={} reason={}", statusCode, reason);
				incrementMessageCounter("closed");
				scheduleReconnect("closed");
			}

			@Override
			public void onError(Throwable error) {
				stopKeepalive();
				markDisconnected("error");
				String type = error == null ? "unknown" : error.getClass().getSimpleName();
				String message = error == null ? "unknown" : error.getMessage();
				lastErrorType = type;
				lastErrorMessage = message;
				lastErrorAtMs.set(System.currentTimeMillis());
				log.warn("price.ws.okx.error type={} error={}", type, message);
				incrementMessageCounter("error");
				scheduleReconnect("error");
			}
		});
	}

	@Override
	public void subscribe(List<PriceQuery> queries) {
		replaceLastQueries(queries);
		if (!client.isConnected()) {
			log.warn("price.ws.okx.subscribe.skip reason=not_connected");
			incrementSubscribeCounter("skip_not_connected");
			return;
		}
		if (queries == null || queries.isEmpty()) {
			incrementSubscribeCounter("skip_empty");
			return;
		}
		List<WsSubscribeArg> targets = collectSubscribeArgs(queries);
		if (targets.isEmpty()) {
			incrementSubscribeCounter("skip_empty_inst");
			return;
		}
		long now = System.currentTimeMillis();
		for (WsSubscribeArg target : targets) {
			String payload = buildSubscribePayload(target);
			if (payload == null || payload.isBlank()) {
				incrementSubscribeCounter("skip_blank_payload");
				continue;
			}
			pendingFirstQuoteAt.put(target.instId(), now);
			client.sendText(payload);
			incrementSubscribeCounter("sent");
			log.info("price.ws.okx.subscribe.sent instType={} instId={}", target.instType(), target.instId());
		}
		log.info("price.ws.okx.subscribe.sent_batch count={} targets={}", targets.size(), targets);
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

	private String buildSubscribePayload(WsSubscribeArg target) {
		if (target == null || target.instId() == null || target.instId().isBlank()) {
			return "";
		}
		List<Map<String, Object>> args = new ArrayList<>();
		Map<String, Object> arg = new HashMap<>();
		arg.put("channel", "tickers");
		arg.put("instType", target.instType());
		arg.put("instId", target.instId());
		args.add(arg);
		Map<String, Object> payload = new HashMap<>();
		payload.put("op", "subscribe");
		payload.put("args", args);
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (Exception ex) {
			incrementSubscribeCounter("payload_failed");
			log.warn("price.ws.okx.subscribe.payload_failed instType={} instId={} error={}",
				target.instType(),
				target.instId(),
				ex.getMessage());
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
			checkFirstQuoteTimeout();
			return;
		}
		client.sendText("ping");
		incrementKeepaliveCounter("ping_send");
		log.debug("price.ws.okx.ping idledMs={}", idleMs);
		checkFirstQuoteTimeout();
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
		List<PriceQuery> queries = new ArrayList<>(lastQueries);
		List<WsSubscribeArg> targets = collectSubscribeArgs(queries);
		lastResubscribeAtMs.set(System.currentTimeMillis());
		lastResubscribeCount.set(targets.size());
		log.info("price.ws.okx.resubscribe.start count={} targets={}", targets.size(), targets);
		subscribe(queries);
	}

	private void scheduleReconnect(String reason) {
		if (stopping.get() || !started.get()) {
			return;
		}
		int attempt = reconnectAttempts.incrementAndGet();
		lastReconnectReason = reason;
		lastReconnectAtMs.set(System.currentTimeMillis());
		int delaySec = computeReconnectDelaySec(attempt);
		if (!reconnectScheduled.compareAndSet(false, true)) {
			log.debug("price.ws.okx.reconnect.skip reason=already_scheduled attempt={} trigger={}", attempt, reason);
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
		log.warn("price.ws.okx.reconnect.scheduled reason={} attempt={} delaySec={}", reason, attempt, delaySec);
		incrementReconnectCounter(reason);
		executor.schedule(() -> {
			if (stopping.get() || !started.get() || client.isConnected()) {
				reconnectScheduled.set(false);
				return;
			}
			// Release schedule flag before connect so a failed connect can schedule next retry.
			reconnectScheduled.set(false);
			log.info("price.ws.okx.reconnect.start attempt={} reason={} url={}", attempt, reason, wsUrl);
			connectInternal();
		}, delaySec, TimeUnit.SECONDS);
	}

	private void stopReconnect() {
		reconnectScheduled.set(false);
		ScheduledExecutorService executor = reconnectExecutor;
		if (executor != null) {
			executor.shutdownNow();
		}
		reconnectExecutor = null;
	}

	private void incrementMessageCounter(String type) {
		meterRegistry.counter(METRIC_WS_MESSAGE_TOTAL, "provider", PROVIDER_NAME, "type", type).increment();
	}

	private void incrementSubscribeCounter(String status) {
		meterRegistry.counter(METRIC_WS_SUBSCRIBE_TOTAL, "provider", PROVIDER_NAME, "status", status).increment();
	}

	private void incrementReconnectCounter(String reason) {
		meterRegistry.counter(METRIC_WS_RECONNECT_TOTAL, "provider", PROVIDER_NAME, "reason", reason).increment();
	}

	
	private long resolveQuoteShortWindowMs() {
		return quoteGuardProperties.getShortWindowMs() > 0L
			? quoteGuardProperties.getShortWindowMs()
			: DEFAULT_QUOTE_SHORT_WINDOW_MS;
	}

	private double resolveQuoteMaxJumpRatio() {
		return quoteGuardProperties.getMaxJumpRatio() > 0D
			? quoteGuardProperties.getMaxJumpRatio()
			: DEFAULT_QUOTE_MAX_JUMP_RATIO;
	}

	private long resolveDropLogIntervalMs() {
		return quoteGuardProperties.getDropLogIntervalMs() > 0L
			? quoteGuardProperties.getDropLogIntervalMs()
			: DEFAULT_DROP_LOG_INTERVAL_MS;
	}

	private void incrementKeepaliveCounter(String action) {
		meterRegistry.counter(METRIC_WS_KEEPALIVE_TOTAL, "provider", PROVIDER_NAME, "action", action).increment();
	}

	private void markDisconnected(String type) {
		lastDisconnectedAtMs.compareAndSet(0L, System.currentTimeMillis());
		meterRegistry.counter(METRIC_WS_DISCONNECT_TOTAL, "provider", PROVIDER_NAME, "type", type).increment();
	}

	private void recordRecoveryDurationIfNeeded() {
		long disconnectedAtMs = lastDisconnectedAtMs.getAndSet(0L);
		if (disconnectedAtMs <= 0L) {
			return;
		}
		long recoveryMs = Math.max(0L, System.currentTimeMillis() - disconnectedAtMs);
		meterRegistry.timer(METRIC_WS_RECOVERY_DURATION, "provider", PROVIDER_NAME)
			.record(recoveryMs, TimeUnit.MILLISECONDS);
	}

	private boolean acceptQuote(PriceStreamQuote quote) {
		if (quote == null || quote.price() == null || quote.ts() <= 0L) {
			recordQuoteDrop("invalid_payload", quote, null, null);
			return false;
		}
		if (quote.price().compareTo(DECIMAL_ZERO) <= 0) {
			recordQuoteDrop("invalid_price", quote, null, null);
			return false;
		}
		String instId = quote.instId();
		if (instId == null || instId.isBlank()) {
			recordQuoteDrop("invalid_inst", quote, null, null);
			return false;
		}
		Long previousTs = lastQuoteTsByInst.get(instId);
		BigDecimal previousPrice = lastPriceByInst.get(instId);
		if (previousTs != null && quote.ts() < previousTs) {
			recordQuoteDrop("ts_rollback", quote, previousTs, previousPrice);
			return false;
		}
		if (previousTs != null && quote.ts() == previousTs) {
			recordQuoteDrop("ts_duplicate", quote, previousTs, previousPrice);
			return false;
		}
		if (previousTs != null && previousPrice != null && previousPrice.compareTo(DECIMAL_ZERO) > 0) {
			long deltaTs = quote.ts() - previousTs;
			if (deltaTs >= 0 && deltaTs <= resolveQuoteShortWindowMs()) {
				double jumpRatio = quote.price()
					.subtract(previousPrice)
					.abs()
					.divide(previousPrice, QUOTE_JUMP_RATIO_SCALE, RoundingMode.HALF_UP)
					.doubleValue();
				if (jumpRatio >= resolveQuoteMaxJumpRatio()) {
					recordQuoteDrop("suspicious_jump", quote, previousTs, previousPrice);
					return false;
				}
			}
		}
		lastQuoteTsByInst.put(instId, quote.ts());
		lastPriceByInst.put(instId, quote.price());
		return true;
	}

	private void recordQuoteDrop(String reason, PriceStreamQuote quote, Long previousTs, BigDecimal previousPrice) {
		String instId = quote == null ? "" : quote.instId();
		BigDecimal currentPrice = quote == null ? null : quote.price();
		Long currentTs = quote == null ? null : quote.ts();
		if (shouldLogQuoteDrop(instId, reason)) {
			log.warn(
				"price.ws.okx.quote.drop reason={} instId={} prevTs={} prevPrice={} currentTs={} currentPrice={}",
				reason,
				instId,
				previousTs,
				previousPrice,
				currentTs,
				currentPrice
			);
		}
		meterRegistry.counter(METRIC_WS_QUOTE_DROPPED_TOTAL, "provider", PROVIDER_NAME, "reason", reason).increment();
	}

	private boolean shouldLogQuoteDrop(String instId, String reason) {
		long now = System.currentTimeMillis();
		long intervalMs = resolveDropLogIntervalMs();
		String key = (instId == null ? "" : instId) + "|" + (reason == null ? "" : reason);
		Long last = lastQuoteDropLogAtByKey.put(key, now);
		return last == null || now - last >= intervalMs;
	}

	private boolean handleControlEventMessage(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		try {
			Map<?, ?> root = objectMapper.readValue(text, Map.class);
			Object eventObj = root.get("event");
			if (eventObj == null) {
				return false;
			}
			String event = String.valueOf(eventObj);
			Object codeObj = root.get("code");
			String code = codeObj == null ? "" : String.valueOf(codeObj);
			Object msgObj = root.get("msg");
			String msg = msgObj == null ? "" : String.valueOf(msgObj);
			String instId = extractInstId(root.get("arg"));
			if ("subscribe".equalsIgnoreCase(event)) {
				log.info("price.ws.okx.subscribe.ack instId={} code={} msg={}", instId, code, msg);
				incrementSubscribeCounter("ack");
				return true;
			}
			if ("error".equalsIgnoreCase(event)) {
				log.warn("price.ws.okx.event.error instId={} code={} msg={}", instId, code, msg);
				incrementSubscribeCounter("ack_error");
				return true;
			}
			log.info("price.ws.okx.event event={} instId={} code={} msg={}", event, instId, code, msg);
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	private String extractInstId(Object argObj) {
		if (!(argObj instanceof Map<?, ?> arg)) {
			return "";
		}
		Object instIdObj = arg.get("instId");
		if (instIdObj == null) {
			return "";
		}
		return String.valueOf(instIdObj);
	}

	private void markFirstQuoteReceived(String instId, long ts) {
		if (instId == null || instId.isBlank()) {
			return;
		}
		Long subscribedAt = pendingFirstQuoteAt.remove(instId);
		if (subscribedAt == null) {
			return;
		}
		long latencyMs = System.currentTimeMillis() - subscribedAt;
		log.info("price.ws.okx.quote.first.by_inst instId={} latencyMs={} quoteTs={}", instId, latencyMs, ts);
	}

	private void checkFirstQuoteTimeout() {
		if (pendingFirstQuoteAt.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		for (Map.Entry<String, Long> entry : pendingFirstQuoteAt.entrySet()) {
			String instId = entry.getKey();
			Long subscribedAt = entry.getValue();
			if (subscribedAt == null) {
				continue;
			}
			long waitMs = now - subscribedAt;
			if (waitMs < FIRST_QUOTE_TIMEOUT_MS) {
				continue;
			}
			if (pendingFirstQuoteAt.remove(instId, subscribedAt)) {
				log.warn("price.ws.okx.quote.first.timeout instId={} waitMs={}", instId, waitMs);
				meterRegistry.counter(METRIC_WS_FIRST_QUOTE_TIMEOUT_TOTAL, "provider", PROVIDER_NAME, "instId", instId).increment();
			}
		}
	}

	@Override
	public PriceStreamProviderStatus currentStatus() {
		return new PriceStreamProviderStatus(
			PROVIDER_NAME,
			started.get(),
			client.isConnected(),
			reconnectScheduled.get(),
			reconnectAttempts.get(),
			lastReconnectReason,
			toInstant(lastReconnectAtMs.get()),
			lastErrorType,
			lastErrorMessage,
			toInstant(lastErrorAtMs.get()),
			lastResubscribeCount.get(),
			toInstant(lastResubscribeAtMs.get()),
			lastQueries.size()
		);
	}

	private Instant toInstant(long epochMs) {
		if (epochMs <= 0L) {
			return null;
		}
		return Instant.ofEpochMilli(epochMs);
	}

	private int computeReconnectDelaySec(int attempt) {
		if (attempt <= 1) {
			return RECONNECT_BASE_DELAY_SEC;
		}
		long delay = (long) RECONNECT_BASE_DELAY_SEC << Math.min(attempt - 1, 8);
		return (int) Math.min(delay, RECONNECT_MAX_DELAY_SEC);
	}

	private List<WsSubscribeArg> collectSubscribeArgs(List<PriceQuery> queries) {
		if (queries == null || queries.isEmpty()) {
			return List.of();
		}
		Map<String, WsSubscribeArg> unique = new LinkedHashMap<>();
		for (PriceQuery query : queries) {
			String instId = buildInstId(query);
			String instType = normalizeOkxInstType(query == null ? null : query.instType());
			if (instId == null || instId.isBlank()) {
				continue;
			}
			String key = instType + "|" + instId;
			unique.putIfAbsent(key, new WsSubscribeArg(instType, instId));
		}
		return new ArrayList<>(unique.values());
	}

	private String normalizeOkxInstType(PriceInstType instType) {
		if (instType == null) {
			return PriceInstType.SPOT.name();
		}
		return instType.name();
	}

	record WsSubscribeArg(String instType, String instId) {
		@Override
		public String toString() {
			return instType + ":" + instId;
		}
	}
}
