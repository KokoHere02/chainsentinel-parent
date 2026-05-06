package com.chainsentinel.web.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.price.api.PriceMarketDataService;
import com.chainsentinel.price.api.PriceMarketStreamService;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PriceOrderBookLevel;
import com.chainsentinel.price.stream.PriceOrderBookEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class PriceMarketFrontendWebSocketHandlerTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void shouldSliceDepthSnapshotAndRealtimePayloadBySubscribedDepth() throws Exception {
		PriceMarketStreamService streamService = mock(PriceMarketStreamService.class);
		PriceMarketDataService marketDataService = mock(PriceMarketDataService.class);
		PriceMarketFrontendWebSocketHandler handler = new PriceMarketFrontendWebSocketHandler(streamService, marketDataService);

		PriceOrderBook fullBook = new PriceOrderBook(
			"okx",
			"BTC-USDT",
			1700000000000L,
			100L,
			12345L,
			List.of(
				new PriceOrderBookLevel(new BigDecimal("101.0"), new BigDecimal("1.0"), 1),
				new PriceOrderBookLevel(new BigDecimal("102.0"), new BigDecimal("2.0"), 1),
				new PriceOrderBookLevel(new BigDecimal("103.0"), new BigDecimal("3.0"), 1)
			),
			List.of(
				new PriceOrderBookLevel(new BigDecimal("100.0"), new BigDecimal("1.5"), 1),
				new PriceOrderBookLevel(new BigDecimal("99.0"), new BigDecimal("2.5"), 1),
				new PriceOrderBookLevel(new BigDecimal("98.0"), new BigDecimal("3.5"), 1)
			)
		);
		when(marketDataService.getOrderBook(eq("okx"), eq("BTC-USDT"), eq(2))).thenReturn(fullBook);

		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn("s1");
		when(session.isOpen()).thenReturn(true);

		handler.afterConnectionEstablished(session);
		handler.handleTextMessage(session, new TextMessage("""
			{"op":"subscribe","instId":"BTC-USDT","depth":2,"channels":["depth"]}
			"""));

		handler.onOrderBook(new PriceOrderBookEvent(fullBook));

		ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
		verify(session, atLeastOnce()).sendMessage(messageCaptor.capture());

		List<TextMessage> messages = messageCaptor.getAllValues();
		JsonNode depthSnapshot = findByType(messages, "depth_snapshot");
		JsonNode depthRealtime = findByType(messages, "depth");

		assertEquals(2, depthSnapshot.path("data").path("asks").size());
		assertEquals(2, depthSnapshot.path("data").path("bids").size());
		assertEquals(2, depthRealtime.path("data").path("asks").size());
		assertEquals(2, depthRealtime.path("data").path("bids").size());
		assertEquals("101.0", depthRealtime.path("data").path("asks").get(0).path("price").asText());

		verify(streamService, atLeastOnce()).refreshSubscriptions(any());
		verify(marketDataService).getOrderBook("okx", "BTC-USDT", 2);
	}

	private JsonNode findByType(List<TextMessage> messages, String type) throws Exception {
		for (TextMessage message : messages) {
			JsonNode node = objectMapper.readTree(message.getPayload());
			if (type.equals(node.path("type").asText())) {
				return node;
			}
		}
		assertTrue(false, "message type not found: " + type);
		return null;
	}
}
