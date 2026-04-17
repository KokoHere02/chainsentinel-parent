package com.chainsentinel.web.ws;

import com.chainsentinel.price.stream.PriceStreamQuote;
import com.chainsentinel.price.stream.PriceStreamQuoteEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class PriceFrontendWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(PriceFrontendWebSocketHandler.class);

	private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		sessions.add(session);
		log.info("ws.frontend.price.connected sessionId={} activeSessions={}", session.getId(), sessions.size());
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(session);
		log.info("ws.frontend.price.closed sessionId={} status={} activeSessions={}",
			session.getId(), status, sessions.size());
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		sessions.remove(session);
		log.warn("ws.frontend.price.error sessionId={} error={}",
			session.getId(), exception == null ? "unknown" : exception.getMessage());
	}

	@EventListener
	public void onPriceQuote(PriceStreamQuoteEvent event) {
		if (event == null || event.quote() == null) {
			return;
		}
		PriceStreamQuote quote = event.quote();
		if (!"okx_ws".equalsIgnoreCase(quote.providerName())) {
			return;
		}
		if (sessions.isEmpty()) {
			return;
		}
		try {
			String payload = objectMapper.writeValueAsString(new PriceWsPushMessage(
				quote.providerName(),
				quote.instId(),
				quote.baseSymbol(),
				quote.quoteSymbol(),
				quote.price(),
				quote.ts()
			));
			TextMessage message = new TextMessage(payload);
			for (WebSocketSession session : sessions) {
				if (!session.isOpen()) {
					sessions.remove(session);
					continue;
				}
				try {
					session.sendMessage(message);
				} catch (Exception ex) {
					sessions.remove(session);
					log.warn("ws.frontend.price.send.failed sessionId={} error={}", session.getId(), ex.getMessage());
				}
			}
		} catch (Exception ex) {
			log.warn("ws.frontend.price.serialize.failed error={}", ex.getMessage());
		}
	}

	record PriceWsPushMessage(
		String provider,
		String instId,
		String base,
		String quote,
		java.math.BigDecimal price,
		long ts
	) {
	}
}

