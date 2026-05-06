package com.chainsentinel.price.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PriceOrderBookLevel;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import com.chainsentinel.price.provider.okx.OkxApiClient;
import com.chainsentinel.price.provider.okx.dto.OkxOrderBookResponse;
import com.chainsentinel.price.stream.PriceOrderBookEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OkxWsMarketDataStreamServiceTest {

	@Mock
	private PriceProviderRuntimeConfig runtimeConfig;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Mock
	private OkxApiClient okxApiClient;

	@Test
	void shouldBuildOrderBookFromSnapshotAndApplyDelta() {
		OkxWsMarketDataStreamService service = new OkxWsMarketDataStreamService(runtimeConfig, eventPublisher, okxApiClient);

		service.handleMessageForTest("""
			{"arg":{"channel":"books","instId":"BTC-USDT"},"action":"snapshot","data":[
			  {"instId":"BTC-USDT","asks":[["101.0","2.0","0","1"]],"bids":[["100.0","1.5","0","2"]],"ts":"1700000000000","seqId":"10","prevSeqId":"-1"}
			]}
			""");

		service.handleMessageForTest("""
			{"arg":{"channel":"books","instId":"BTC-USDT"},"action":"update","data":[
			  {"instId":"BTC-USDT","asks":[["101.0","3.0","0","1"],["102.0","1.0","0","1"]],"bids":[["100.0","0","0","0"],["99.5","4.0","0","3"]],"ts":"1700000001000","seqId":"11","prevSeqId":"10"}
			]}
			""");

		PriceOrderBook snapshot = service.currentOrderBookForTest("BTC-USDT");
		assertNotNull(snapshot);
		assertEquals(11L, snapshot.seqId());
		assertEquals(2, snapshot.asks().size());
		assertEquals("101.0", snapshot.asks().get(0).price().toPlainString());
		assertEquals("3.0", snapshot.asks().get(0).size().toPlainString());
		assertEquals(1, snapshot.bids().size());
		assertEquals("99.5", snapshot.bids().get(0).price().toPlainString());

		verify(eventPublisher, atLeastOnce()).publishEvent(argThat((Object event) ->
			event instanceof PriceOrderBookEvent priceOrderBookEvent
				&& "BTC-USDT".equals(priceOrderBookEvent.orderBook().instId())
		));
	}

	@Test
	void shouldRecoverFromRestSnapshotWhenSequenceBreaks() {
		when(okxApiClient.fetchOrderBook(eq("BTC-USDT"), eq(400))).thenReturn(Optional.of(
			new OkxOrderBookResponse(
				"BTC-USDT",
				1700000002000L,
				20L,
				12345L,
				List.of(new PriceOrderBookLevel(new BigDecimal("101.5"), new BigDecimal("5.0"), 2)),
				List.of(new PriceOrderBookLevel(new BigDecimal("101.4"), new BigDecimal("6.0"), 3))
			)
		));

		OkxWsMarketDataStreamService service = new OkxWsMarketDataStreamService(runtimeConfig, eventPublisher, okxApiClient);

		service.handleMessageForTest("""
			{"arg":{"channel":"books","instId":"BTC-USDT"},"action":"snapshot","data":[
			  {"instId":"BTC-USDT","asks":[["101.0","2.0","0","1"]],"bids":[["100.0","1.5","0","2"]],"ts":"1700000000000","seqId":"10","prevSeqId":"-1"}
			]}
			""");

		service.handleMessageForTest("""
			{"arg":{"channel":"books","instId":"BTC-USDT"},"action":"update","data":[
			  {"instId":"BTC-USDT","asks":[["101.0","2.5","0","1"]],"bids":[["100.0","1.0","0","2"]],"ts":"1700000001000","seqId":"21","prevSeqId":"15"}
			]}
			""");

		PriceOrderBook snapshot = service.currentOrderBookForTest("BTC-USDT");
		assertNotNull(snapshot);
		assertEquals(20L, snapshot.seqId());
		assertEquals("101.5", snapshot.asks().get(0).price().toPlainString());
		assertEquals("101.4", snapshot.bids().get(0).price().toPlainString());

		verify(okxApiClient).fetchOrderBook("BTC-USDT", 400);
	}

	@Test
	void shouldRecoverFromRestSnapshotWhenChecksumMismatch() {
		when(okxApiClient.fetchOrderBook(eq("BTC-USDT"), eq(400))).thenReturn(Optional.of(
			new OkxOrderBookResponse(
				"BTC-USDT",
				1700000003000L,
				30L,
				99999L,
				List.of(new PriceOrderBookLevel(new BigDecimal("102.0"), new BigDecimal("7.0"), 2)),
				List.of(new PriceOrderBookLevel(new BigDecimal("101.9"), new BigDecimal("8.0"), 3))
			)
		));

		OkxWsMarketDataStreamService service = new OkxWsMarketDataStreamService(runtimeConfig, eventPublisher, okxApiClient);

		service.handleMessageForTest("""
			{"arg":{"channel":"books","instId":"BTC-USDT"},"action":"snapshot","data":[
			  {"instId":"BTC-USDT","asks":[["101.0","2.0","0","1"]],"bids":[["100.0","1.5","0","2"]],"ts":"1700000000000","seqId":"10","prevSeqId":"-1","checksum":"1"}
			]}
			""");

		PriceOrderBook snapshot = service.currentOrderBookForTest("BTC-USDT");
		assertNotNull(snapshot);
		assertEquals(30L, snapshot.seqId());
		assertEquals("102.0", snapshot.asks().get(0).price().toPlainString());
		assertEquals("101.9", snapshot.bids().get(0).price().toPlainString());

		verify(okxApiClient).fetchOrderBook("BTC-USDT", 400);
	}
}
