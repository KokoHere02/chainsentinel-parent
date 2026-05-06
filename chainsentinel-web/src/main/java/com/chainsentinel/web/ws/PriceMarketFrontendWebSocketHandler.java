package com.chainsentinel.web.ws;

import com.chainsentinel.price.api.PriceMarketDataService;
import com.chainsentinel.price.api.PriceMarketStreamService;
import com.chainsentinel.price.api.dto.PriceMarketChannel;
import com.chainsentinel.price.api.dto.PriceMarketSubscription;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import com.chainsentinel.price.stream.PriceOrderBookEvent;
import com.chainsentinel.price.stream.PricePublicTradeEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class PriceMarketFrontendWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(PriceMarketFrontendWebSocketHandler.class);
	private static final String DEFAULT_PROVIDER = "okx";
	private static final int DEFAULT_DEPTH = 20;
	private static final int MAX_DEPTH = 400;

	private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
	private final Map<String, Set<PriceMarketSubscription>> sessionSubscriptions = new ConcurrentHashMap<>();
	private final PriceMarketStreamService priceMarketStreamService;
	private final PriceMarketDataService priceMarketDataService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public PriceMarketFrontendWebSocketHandler(
		PriceMarketStreamService priceMarketStreamService,
		PriceMarketDataService priceMarketDataService
	) {
		this.priceMarketStreamService = priceMarketStreamService;
		this.priceMarketDataService = priceMarketDataService;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		sessions.put(session.getId(), session);
		sessionSubscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
		log.info("ws.frontend.market.connected sessionId={} activeSessions={}", session.getId(), sessions.size());
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
			try {
				JsonNode root = objectMapper.readTree(message.getPayload());
				String op = root.path("op").asText();
				String provider = root.path("provider").asText(DEFAULT_PROVIDER);
				String instId = root.path("instId").asText(null);
				int depth = parseDepth(root.path("depth"));
				List<PriceMarketChannel> channels = parseChannels(root.path("channels"));
				if ("subscribe".equalsIgnoreCase(op)) {
					subscribe(session, provider, instId, channels, depth);
					return;
				}
				if ("unsubscribe".equalsIgnoreCase(op)) {
					unsubscribe(session, provider, instId, channels);
				return;
			}
			send(session, Map.of("type", "error", "message", "unsupported op"));
		} catch (Exception ex) {
			send(session, Map.of("type", "error", "message", "invalid payload"));
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(session.getId());
		sessionSubscriptions.remove(session.getId());
		refreshUpstreamSubscriptions();
		log.info("ws.frontend.market.closed sessionId={} status={} activeSessions={}",
			session.getId(), status, sessions.size());
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		sessions.remove(session.getId());
		sessionSubscriptions.remove(session.getId());
		refreshUpstreamSubscriptions();
		log.warn("ws.frontend.market.error sessionId={} error={}",
			session.getId(), exception == null ? "unknown" : exception.getMessage());
	}

	@EventListener
	public void onOrderBook(PriceOrderBookEvent event) {
		if (event == null || event.orderBook() == null) {
			return;
		}
		PriceOrderBook orderBook = event.orderBook();
		broadcastToSubscribers(
			new PriceMarketSubscription(orderBook.provider(), orderBook.instId(), PriceMarketChannel.DEPTH, null),
			orderBook,
			"type",
			"depth"
		);
	}

	@EventListener
	public void onPublicTrade(PricePublicTradeEvent event) {
		if (event == null || event.trade() == null) {
			return;
		}
		PricePublicTrade trade = event.trade();
		broadcastToSubscribers(
			new PriceMarketSubscription(trade.provider(), trade.instId(), PriceMarketChannel.TRADES, null),
			Map.of("type", "trade", "data", trade)
		);
	}

	private void subscribe(WebSocketSession session, String provider, String instId, List<PriceMarketChannel> channels, int depth) {
		if (instId == null || instId.isBlank() || channels.isEmpty()) {
			send(session, Map.of("type", "error", "message", "instId and channels are required"));
			return;
		}
		Set<PriceMarketSubscription> subscriptions = sessionSubscriptions.computeIfAbsent(
			session.getId(),
			ignored -> ConcurrentHashMap.newKeySet()
		);
		for (PriceMarketChannel channel : channels) {
			subscriptions.add(new PriceMarketSubscription(
				normalizeProvider(provider),
				normalizeInstId(instId),
				channel,
				channel == PriceMarketChannel.DEPTH ? depth : null
			));
		}
		refreshUpstreamSubscriptions();
		sendSnapshot(session, normalizeProvider(provider), normalizeInstId(instId), channels, depth);
		send(session, Map.of("type", "subscribed", "instId", normalizeInstId(instId), "channels", channels, "depth", depth));
	}

	private void unsubscribe(WebSocketSession session, String provider, String instId, List<PriceMarketChannel> channels) {
		Set<PriceMarketSubscription> subscriptions = sessionSubscriptions.get(session.getId());
		if (subscriptions == null || subscriptions.isEmpty()) {
			return;
		}
		if (channels.isEmpty()) {
			subscriptions.removeIf(item -> item.instId().equals(normalizeInstId(instId)));
		} else {
			for (PriceMarketChannel channel : channels) {
				subscriptions.removeIf(item ->
					item.channel() == channel
						&& item.instId().equals(normalizeInstId(instId))
						&& item.provider().equals(normalizeProvider(provider))
				);
			}
		}
		refreshUpstreamSubscriptions();
		send(session, Map.of("type", "unsubscribed", "instId", normalizeInstId(instId), "channels", channels));
	}

	private void sendSnapshot(WebSocketSession session, String provider, String instId, List<PriceMarketChannel> channels, int depth) {
		for (PriceMarketChannel channel : channels) {
			try {
				if (channel == PriceMarketChannel.DEPTH) {
					PriceOrderBook orderBook = priceMarketDataService.getOrderBook(provider, instId, depth);
					send(session, Map.of("type", "depth_snapshot", "data", sliceOrderBook(orderBook, depth)));
					continue;
				}
				List<PricePublicTrade> trades = priceMarketDataService.getRecentPublicTrades(provider, instId, 10);
				send(session, Map.of("type", "trade_snapshot", "data", trades));
			} catch (Exception ex) {
				log.debug("ws.frontend.market.snapshot.failed sessionId={} instId={} channel={} error={}",
					session.getId(), instId, channel, ex.getMessage());
			}
		}
	}

	private void refreshUpstreamSubscriptions() {
		Set<PriceMarketSubscription> union = new LinkedHashSet<>();
		for (Set<PriceMarketSubscription> subscriptions : sessionSubscriptions.values()) {
			for (PriceMarketSubscription subscription : subscriptions) {
				union.add(new PriceMarketSubscription(subscription.provider(), subscription.instId(), subscription.channel(), null));
			}
		}
		priceMarketStreamService.refreshSubscriptions(new ArrayList<>(union));
	}

	private void broadcastToSubscribers(PriceMarketSubscription target, PriceOrderBook orderBook, String typeKey, String typeValue) {
		if (sessions.isEmpty()) {
			return;
		}
		for (WebSocketSession session : sessions.values()) {
			if (!session.isOpen()) {
				sessions.remove(session.getId());
				sessionSubscriptions.remove(session.getId());
				continue;
			}
			Set<PriceMarketSubscription> subscriptions = sessionSubscriptions.get(session.getId());
			if (subscriptions == null) {
				continue;
			}
			for (PriceMarketSubscription subscription : subscriptions) {
				if (!matches(subscription, target)) {
					continue;
				}
				int depth = normalizeDepth(subscription.depth());
				send(session, Map.of(typeKey, typeValue, "data", sliceOrderBook(orderBook, depth)));
			}
		}
	}

	private void broadcastToSubscribers(PriceMarketSubscription target, Map<String, Object> payload) {
		if (sessions.isEmpty()) {
			return;
		}
		for (WebSocketSession session : sessions.values()) {
			if (!session.isOpen()) {
				sessions.remove(session.getId());
				sessionSubscriptions.remove(session.getId());
				continue;
			}
			Set<PriceMarketSubscription> subscriptions = sessionSubscriptions.get(session.getId());
			if (subscriptions == null) {
				continue;
			}
			for (PriceMarketSubscription subscription : subscriptions) {
				if (matches(subscription, target)) {
					send(session, payload);
					break;
				}
			}
		}
	}

	private void send(WebSocketSession session, Object payload) {
		if (session == null || !session.isOpen()) {
			return;
		}
		try {
			session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
		} catch (Exception ex) {
			sessions.remove(session.getId());
			sessionSubscriptions.remove(session.getId());
			log.warn("ws.frontend.market.send.failed sessionId={} error={}", session.getId(), ex.getMessage());
		}
	}

	private List<PriceMarketChannel> parseChannels(JsonNode node) {
		List<PriceMarketChannel> channels = new ArrayList<>();
		if (!node.isArray()) {
			return channels;
		}
		for (JsonNode item : node) {
			try {
				channels.add(PriceMarketChannel.valueOf(item.asText().trim().toUpperCase()));
			} catch (Exception ignore) {
				// ignore invalid channels
			}
		}
		return channels;
	}

	private String normalizeProvider(String provider) {
		if (provider == null || provider.isBlank() || "okx_ws".equalsIgnoreCase(provider)) {
			return DEFAULT_PROVIDER;
		}
		return provider.trim().toLowerCase();
	}

	private String normalizeInstId(String instId) {
		if (instId == null) {
			return null;
		}
		return instId.trim().toUpperCase();
	}

	private int parseDepth(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return DEFAULT_DEPTH;
		}
		return normalizeDepth(node.asInt(DEFAULT_DEPTH));
	}

	private int normalizeDepth(Integer depth) {
		if (depth == null) {
			return DEFAULT_DEPTH;
		}
		return Math.max(1, Math.min(MAX_DEPTH, depth));
	}

	private boolean matches(PriceMarketSubscription subscription, PriceMarketSubscription target) {
		return subscription != null
			&& target != null
			&& subscription.channel() == target.channel()
			&& normalizeProvider(subscription.provider()).equals(normalizeProvider(target.provider()))
			&& normalizeInstId(subscription.instId()).equals(normalizeInstId(target.instId()));
	}

	private PriceOrderBook sliceOrderBook(PriceOrderBook orderBook, int depth) {
		if (orderBook == null) {
			return null;
		}
		int normalizedDepth = normalizeDepth(depth);
		List<com.chainsentinel.price.api.dto.PriceOrderBookLevel> asks = orderBook.asks() == null
			? List.of()
			: orderBook.asks().stream().limit(normalizedDepth).toList();
		List<com.chainsentinel.price.api.dto.PriceOrderBookLevel> bids = orderBook.bids() == null
			? List.of()
			: orderBook.bids().stream().limit(normalizedDepth).toList();
		return new PriceOrderBook(
			orderBook.provider(),
			orderBook.instId(),
			orderBook.ts(),
			orderBook.seqId(),
			orderBook.checksum(),
			asks,
			bids
		);
	}
}
