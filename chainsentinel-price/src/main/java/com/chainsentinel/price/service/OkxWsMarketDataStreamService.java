package com.chainsentinel.price.service;

import com.chainsentinel.price.api.PriceMarketStreamService;
import com.chainsentinel.price.api.PublicMarketDataClient;
import com.chainsentinel.price.api.PublicMarketDataClientRouter;
import com.chainsentinel.price.api.dto.PriceMarketChannel;
import com.chainsentinel.price.api.dto.PriceMarketSubscription;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PriceOrderBookLevel;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import com.chainsentinel.price.stream.PriceOrderBookEvent;
import com.chainsentinel.price.stream.PricePublicTradeEvent;
import com.chainsentinel.price.stream.ws.SimpleWebSocketClient;
import com.chainsentinel.price.stream.ws.WebSocketMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class OkxWsMarketDataStreamService implements PriceMarketStreamService {

	private static final Logger log = LoggerFactory.getLogger(OkxWsMarketDataStreamService.class);
	private static final String PROVIDER_NAME = "okx";
	private static final String DEFAULT_WS_URL = "wss://ws.okx.com:8443/ws/v5/public";
	private static final int REST_RECOVERY_DEPTH = 400;
	private static final int CHECKSUM_DEPTH = 25;
	private static final long RECONNECT_DELAY_SEC = 3L;

	private final PriceProviderRuntimeConfig runtimeConfig;
	private final ApplicationEventPublisher eventPublisher;
	private final PublicMarketDataClient marketDataClient;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final SimpleWebSocketClient client = new SimpleWebSocketClient(Duration.ofSeconds(10));
	private final Set<PriceMarketSubscription> desiredSubscriptions = ConcurrentHashMap.newKeySet();
	private final Set<PriceMarketSubscription> activeSubscriptions = ConcurrentHashMap.newKeySet();
	private final Map<String, OrderBookState> orderBooksByInst = new ConcurrentHashMap<>();
	private final AtomicBoolean started = new AtomicBoolean(false);
	private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
	private volatile ScheduledExecutorService reconnectExecutor;
	private volatile boolean stopping;
	private volatile String wsUrl;

	public OkxWsMarketDataStreamService(
		PriceProviderRuntimeConfig runtimeConfig,
		ApplicationEventPublisher eventPublisher,
		PublicMarketDataClientRouter marketDataClientRouter
	) {
		this.runtimeConfig = runtimeConfig;
		this.eventPublisher = eventPublisher;
		this.marketDataClient = marketDataClientRouter.resolve(PROVIDER_NAME);
	}

	@Override
	public synchronized void refreshSubscriptions(List<PriceMarketSubscription> subscriptions) {
		Set<PriceMarketSubscription> normalized = normalizeSubscriptions(subscriptions);
		Set<PriceMarketSubscription> previous = new LinkedHashSet<>(desiredSubscriptions);
		desiredSubscriptions.clear();
		desiredSubscriptions.addAll(normalized);
		pruneUnusedOrderBooks();
		if (desiredSubscriptions.isEmpty()) {
			activeSubscriptions.clear();
			stopping = true;
			client.close();
			started.set(false);
			return;
		}
		stopping = false;
		if (started.compareAndSet(false, true)) {
			wsUrl = runtimeConfig.providerBaseUrl("okx_ws", DEFAULT_WS_URL);
			connectInternal();
			return;
		}
		if (!client.isConnected()) {
			return;
		}
		Set<PriceMarketSubscription> toUnsubscribe = new LinkedHashSet<>(previous);
		toUnsubscribe.removeAll(normalized);
		Set<PriceMarketSubscription> toSubscribe = new LinkedHashSet<>(normalized);
		toSubscribe.removeAll(activeSubscriptions);
		sendOperation("unsubscribe", toUnsubscribe);
		sendOperation("subscribe", toSubscribe);
		activeSubscriptions.removeAll(toUnsubscribe);
		activeSubscriptions.addAll(toSubscribe);
	}

	private void connectInternal() {
		String url = wsUrl;
		if (url == null || url.isBlank()) {
			started.set(false);
			return;
		}
		client.connect(url, new WebSocketMessageHandler() {
			@Override
			public void onOpen() {
				reconnectScheduled.set(false);
				log.info("price.ws.market.okx.connected url={}", url);
				Set<PriceMarketSubscription> snapshot = new LinkedHashSet<>(desiredSubscriptions);
				sendOperation("subscribe", snapshot);
				activeSubscriptions.clear();
				activeSubscriptions.addAll(snapshot);
			}

			@Override
			public void onText(String text) {
				handleMessage(text);
			}

			@Override
			public void onClose(int statusCode, String reason) {
				activeSubscriptions.clear();
				log.warn("price.ws.market.okx.closed status={} reason={}", statusCode, reason);
				if (!stopping && !desiredSubscriptions.isEmpty()) {
					recoverAllOrderBooks("ws_closed");
					scheduleReconnect();
				}
			}

			@Override
			public void onError(Throwable error) {
				activeSubscriptions.clear();
				log.warn("price.ws.market.okx.error error={}", error == null ? "unknown" : error.getMessage());
				if (!stopping && !desiredSubscriptions.isEmpty()) {
					recoverAllOrderBooks("ws_error");
					scheduleReconnect();
				}
			}
		});
	}

	private void handleMessage(String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		String trimmed = text.trim();
		if ("ping".equalsIgnoreCase(trimmed)) {
			client.sendText("pong");
			return;
		}
		if ("pong".equalsIgnoreCase(trimmed)) {
			return;
		}
		try {
			JsonNode root = objectMapper.readTree(trimmed);
			String channel = root.path("arg").path("channel").asText();
			JsonNode data = root.path("data");
			if ("books".equals(channel)) {
				handleBooks(root.path("action").asText(), root.path("arg").path("instId").asText(), data);
				return;
			}
			if ("trades".equals(channel)) {
				publishTrades(data);
			}
		} catch (Exception ex) {
			log.debug("price.ws.market.okx.parse.failed error={}", ex.getMessage());
		}
	}

	void handleMessageForTest(String text) {
		handleMessage(text);
	}

	PriceOrderBook currentOrderBookForTest(String instId) {
		String normalizedInstId = normalizeInstId(instId);
		if (normalizedInstId == null) {
			return null;
		}
		OrderBookState state = orderBooksByInst.get(normalizedInstId);
		if (state == null) {
			return null;
		}
		synchronized (state) {
			return state.snapshot();
		}
	}

	private void handleBooks(String action, String instId, JsonNode data) {
		if (!data.isArray() || data.isEmpty()) {
			return;
		}
		JsonNode first = data.get(0);
		String normalizedInstId = normalizeInstId(first.path("instId").asText(instId));
		if (normalizedInstId == null) {
			return;
		}
		long seqId = parseLongPrimitive(first.path("seqId").asText(), -1L);
		long prevSeqId = parseLongPrimitive(first.path("prevSeqId").asText(), -1L);
		long checksum = parseLongPrimitive(first.path("checksum").asText(), Long.MIN_VALUE);
		long ts = parseLongPrimitive(first.path("ts").asText(), System.currentTimeMillis());
		OrderBookState state = orderBooksByInst.computeIfAbsent(normalizedInstId, ignored -> new OrderBookState(normalizedInstId));
		boolean accepted;
		synchronized (state) {
			if ("snapshot".equalsIgnoreCase(action) || state.seqId == null) {
				state.replace(first.path("asks"), first.path("bids"), seqId, checksum, ts);
				accepted = validateChecksum(state, checksum, normalizedInstId, seqId, prevSeqId, "snapshot");
			} else {
				accepted = state.applyDelta(first.path("asks"), first.path("bids"), prevSeqId, seqId, checksum, ts)
					&& validateChecksum(state, checksum, normalizedInstId, seqId, prevSeqId, "update");
			}
		}
		if (!accepted) {
			recoverSingleOrderBook(normalizedInstId, "books_sequence_or_checksum");
			return;
		}
		publishOrderBook(state.snapshot());
	}

	private boolean validateChecksum(
		OrderBookState state,
		long expectedChecksum,
		String instId,
		long seqId,
		long prevSeqId,
		String action
	) {
		if (expectedChecksum == Long.MIN_VALUE) {
			return true;
		}
		long actualChecksum = state.computeChecksum();
		if (actualChecksum == expectedChecksum) {
			state.checksum = actualChecksum;
			return true;
		}
		log.warn(
			"price.ws.market.okx.books.checksum_mismatch instId={} action={} seqId={} prevSeqId={} expected={} actual={}",
			instId, action, seqId, prevSeqId, expectedChecksum, actualChecksum
		);
		return false;
	}

	private void publishOrderBook(PriceOrderBook orderBook) {
		eventPublisher.publishEvent(new PriceOrderBookEvent(orderBook));
	}

	private void publishTrades(JsonNode data) {
		if (!data.isArray()) {
			return;
		}
		for (JsonNode row : data) {
			try {
				PricePublicTrade trade = new PricePublicTrade(
					PROVIDER_NAME,
					row.path("instId").asText(),
					row.path("tradeId").asText(null),
					new BigDecimal(row.path("px").asText()),
					new BigDecimal(row.path("sz").asText()),
					row.path("side").asText(null),
					parseLong(row.path("ts").asText())
				);
				eventPublisher.publishEvent(new PricePublicTradeEvent(trade));
			} catch (Exception ignore) {
				// ignore malformed rows
			}
		}
	}

	private void recoverAllOrderBooks(String reason) {
		for (String instId : subscribedInstIds()) {
			recoverSingleOrderBook(instId, reason);
		}
	}

	private void recoverSingleOrderBook(String instId, String reason) {
		if (instId == null || instId.isBlank()) {
			return;
		}
		marketDataClient.getOrderBook(instId, REST_RECOVERY_DEPTH).ifPresentOrElse(response -> {
			OrderBookState state = orderBooksByInst.computeIfAbsent(instId, ignored -> new OrderBookState(instId));
			synchronized (state) {
				state.replace(response);
			}
			log.info("price.ws.market.okx.books.recovered instId={} reason={} seqId={} checksum={} depth={}",
				instId, reason, response.seqId(), response.checksum(), Math.max(response.asks().size(), response.bids().size()));
			publishOrderBook(state.snapshot());
		}, () -> log.warn("price.ws.market.okx.books.recover_failed instId={} reason={}", instId, reason));
	}

	private void sendOperation(String op, Set<PriceMarketSubscription> subscriptions) {
		if (subscriptions == null || subscriptions.isEmpty() || !client.isConnected()) {
			return;
		}
		List<Map<String, String>> args = buildArgs(subscriptions);
		if (args.isEmpty()) {
			return;
		}
		try {
			Map<String, Object> message = new ConcurrentHashMap<>();
			message.put("op", op);
			message.put("args", args);
			client.sendText(objectMapper.writeValueAsString(message));
			log.info("price.ws.market.okx.{} count={}", op, args.size());
		} catch (Exception ex) {
			log.warn("price.ws.market.okx.{}.failed error={}", op, ex.getMessage());
		}
	}

	private List<Map<String, String>> buildArgs(Set<PriceMarketSubscription> subscriptions) {
		List<Map<String, String>> args = new ArrayList<>();
		for (PriceMarketSubscription subscription : subscriptions) {
			String instId = normalizeInstId(subscription.instId());
			if (instId == null) {
				continue;
			}
			String channel = subscription.channel() == PriceMarketChannel.DEPTH ? "books" : "trades";
			args.add(Map.of("channel", channel, "instId", instId));
		}
		return args;
	}

	private Set<PriceMarketSubscription> normalizeSubscriptions(List<PriceMarketSubscription> subscriptions) {
		Set<PriceMarketSubscription> normalized = new LinkedHashSet<>();
		if (subscriptions == null) {
			return normalized;
		}
		for (PriceMarketSubscription subscription : subscriptions) {
			if (subscription == null || subscription.channel() == null) {
				continue;
			}
			String provider = subscription.provider();
			if (provider != null && !provider.isBlank()
				&& !"okx".equalsIgnoreCase(provider)
				&& !"okx_ws".equalsIgnoreCase(provider)) {
				continue;
			}
			String instId = normalizeInstId(subscription.instId());
			if (instId == null) {
				continue;
			}
			normalized.add(new PriceMarketSubscription(PROVIDER_NAME, instId, subscription.channel(), null));
		}
		return normalized;
	}

	private void pruneUnusedOrderBooks() {
		Set<String> instIds = subscribedInstIds();
		orderBooksByInst.keySet().removeIf(instId -> !instIds.contains(instId));
	}

	private Set<String> subscribedInstIds() {
		Set<String> instIds = new LinkedHashSet<>();
		for (PriceMarketSubscription subscription : desiredSubscriptions) {
			if (subscription.channel() == PriceMarketChannel.DEPTH && subscription.instId() != null) {
				instIds.add(subscription.instId());
			}
		}
		return instIds;
	}

	private String normalizeInstId(String instId) {
		if (instId == null || instId.isBlank()) {
			return null;
		}
		return instId.trim().toUpperCase(Locale.ROOT);
	}

	private Long parseLong(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Long.valueOf(value);
		} catch (Exception ex) {
			return null;
		}
	}

	private long parseLongPrimitive(String value, long defaultValue) {
		Long parsed = parseLong(value);
		return parsed == null ? defaultValue : parsed;
	}

	private void scheduleReconnect() {
		if (!reconnectScheduled.compareAndSet(false, true)) {
			return;
		}
		ScheduledExecutorService executor = reconnectExecutor;
		if (executor == null || executor.isShutdown()) {
			ThreadFactory factory = runnable -> {
				Thread thread = new Thread(runnable);
				thread.setName("okx-market-ws-reconnect");
				thread.setDaemon(true);
				return thread;
			};
			executor = Executors.newSingleThreadScheduledExecutor(factory);
			reconnectExecutor = executor;
		}
		executor.schedule(() -> {
			reconnectScheduled.set(false);
			if (stopping || desiredSubscriptions.isEmpty()) {
				return;
			}
			client.close();
			connectInternal();
		}, RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
	}

	private static final class OrderBookState {

		private final String instId;
		private final TreeMap<BigDecimal, BookLevel> asks = new TreeMap<>();
		private final TreeMap<BigDecimal, BookLevel> bids = new TreeMap<>((left, right) -> right.compareTo(left));
		private Long ts;
		private Long seqId;
		private Long checksum;

		private OrderBookState(String instId) {
			this.instId = instId;
		}

		private void replace(JsonNode asksNode, JsonNode bidsNode, long seqId, long checksum, long ts) {
			asks.clear();
			bids.clear();
			merge(asks, asksNode);
			merge(bids, bidsNode);
			this.seqId = seqId >= 0 ? seqId : null;
			this.checksum = checksum == Long.MIN_VALUE ? null : checksum;
			this.ts = ts > 0 ? ts : null;
		}

		private void replace(PriceOrderBook response) {
			asks.clear();
			bids.clear();
			merge(asks, response.asks());
			merge(bids, response.bids());
			this.seqId = response.seqId();
			this.checksum = response.checksum();
			this.ts = response.ts();
		}

		private boolean applyDelta(
			JsonNode asksNode,
			JsonNode bidsNode,
			long prevSeqId,
			long seqId,
			long checksum,
			long ts
		) {
			if (this.seqId == null || prevSeqId < 0 || !this.seqId.equals(prevSeqId)) {
				return false;
			}
			merge(asks, asksNode);
			merge(bids, bidsNode);
			this.seqId = seqId >= 0 ? seqId : this.seqId;
			this.checksum = checksum == Long.MIN_VALUE ? null : checksum;
			this.ts = ts > 0 ? ts : this.ts;
			return true;
		}

		private void merge(TreeMap<BigDecimal, BookLevel> book, JsonNode rows) {
			if (!rows.isArray()) {
				return;
			}
			for (JsonNode row : rows) {
				if (!row.isArray() || row.size() < 2) {
					continue;
				}
				try {
					BigDecimal price = new BigDecimal(row.get(0).asText());
					String sizeText = row.get(1).asText();
					BigDecimal size = new BigDecimal(sizeText);
					if (size.compareTo(BigDecimal.ZERO) == 0) {
						book.remove(price);
						continue;
					}
					Integer orderCount = row.size() > 3 ? Integer.valueOf(row.get(3).asText()) : null;
					book.put(price, new BookLevel(row.get(0).asText(), sizeText, price, size, orderCount));
				} catch (Exception ignore) {
					// ignore malformed rows
				}
			}
		}

		private void merge(TreeMap<BigDecimal, BookLevel> book, List<PriceOrderBookLevel> rows) {
			if (rows == null) {
				return;
			}
			for (PriceOrderBookLevel row : rows) {
				if (row == null || row.price() == null || row.size() == null) {
					continue;
				}
				if (row.size().compareTo(BigDecimal.ZERO) == 0) {
					book.remove(row.price());
					continue;
				}
				book.put(row.price(), new BookLevel(
					row.price().toPlainString(),
					row.size().toPlainString(),
					row.price(),
					row.size(),
					row.orderCount()
				));
			}
		}

		private long computeChecksum() {
			List<BookLevel> topBids = takeTop(bids, CHECKSUM_DEPTH);
			List<BookLevel> topAsks = takeTop(asks, CHECKSUM_DEPTH);
			StringBuilder builder = new StringBuilder();
			int max = Math.max(topBids.size(), topAsks.size());
			for (int index = 0; index < max; index++) {
				if (index < topBids.size()) {
					appendChecksumItem(builder, topBids.get(index));
				}
				if (index < topAsks.size()) {
					appendChecksumItem(builder, topAsks.get(index));
				}
			}
			CRC32 crc32 = new CRC32();
			crc32.update(builder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return (int) crc32.getValue();
		}

		private PriceOrderBook snapshot() {
			return new PriceOrderBook(
				PROVIDER_NAME,
				instId,
				ts,
				seqId,
				checksum == null ? computeChecksum() : checksum,
				toView(asks),
				toView(bids)
			);
		}

		private List<BookLevel> takeTop(TreeMap<BigDecimal, BookLevel> book, int limit) {
			List<BookLevel> result = new ArrayList<>(Math.min(limit, book.size()));
			Iterator<BookLevel> iterator = book.values().iterator();
			while (iterator.hasNext() && result.size() < limit) {
				result.add(iterator.next());
			}
			return result;
		}

		private List<PriceOrderBookLevel> toView(TreeMap<BigDecimal, BookLevel> book) {
			List<PriceOrderBookLevel> result = new ArrayList<>(book.size());
			for (BookLevel level : book.values()) {
				result.add(new PriceOrderBookLevel(level.price(), level.size(), level.orderCount()));
			}
			return result;
		}

		private void appendChecksumItem(StringBuilder builder, BookLevel level) {
			if (builder.length() > 0) {
				builder.append(':');
			}
			builder.append(level.priceText()).append(':').append(level.sizeText());
		}
	}

	private record BookLevel(
		String priceText,
		String sizeText,
		BigDecimal price,
		BigDecimal size,
		Integer orderCount
	) {
	}
}
