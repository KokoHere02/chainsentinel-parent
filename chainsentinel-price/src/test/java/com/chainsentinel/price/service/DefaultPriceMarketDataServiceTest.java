package com.chainsentinel.price.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PriceOrderBookLevel;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import com.chainsentinel.price.provider.okx.OkxApiClient;
import com.chainsentinel.price.provider.okx.dto.OkxOrderBookResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultPriceMarketDataServiceTest {

	@Mock
	private OkxApiClient okxApiClient;

	@Test
	void shouldReturnOrderBookFromOkx() {
		when(okxApiClient.fetchOrderBook(eq("BTC-USDT"), eq(20))).thenReturn(Optional.of(
			new OkxOrderBookResponse(
				"BTC-USDT",
				1700000000000L,
				1001L,
				12345L,
				List.of(new PriceOrderBookLevel(new BigDecimal("70100.1"), new BigDecimal("1.25"), 3)),
				List.of(new PriceOrderBookLevel(new BigDecimal("70100.0"), new BigDecimal("0.80"), 2))
			)
		));

		DefaultPriceMarketDataService service = new DefaultPriceMarketDataService(okxApiClient);
		PriceOrderBook result = service.getOrderBook("okx", "BTC-USDT", 20);

		assertEquals("okx", result.provider());
		assertEquals("BTC-USDT", result.instId());
		assertEquals(1001L, result.seqId());
		assertEquals(1, result.asks().size());
		assertEquals("70100.1", result.asks().get(0).price().toPlainString());
	}

	@Test
	void shouldReturnRecentTradesFromOkx() {
		when(okxApiClient.fetchRecentPublicTrades(eq("BTC-USDT"), eq(10))).thenReturn(List.of(
			new PricePublicTrade("okx", "BTC-USDT", "1001", new BigDecimal("70100.1"), new BigDecimal("0.01"), "buy", 1700000000000L)
		));

		DefaultPriceMarketDataService service = new DefaultPriceMarketDataService(okxApiClient);
		List<PricePublicTrade> result = service.getRecentPublicTrades("okx_ws", "BTC-USDT", 10);

		assertEquals(1, result.size());
		assertEquals("1001", result.get(0).tradeId());
		assertEquals("buy", result.get(0).side());
	}

	@Test
	void shouldRejectUnsupportedProvider() {
		DefaultPriceMarketDataService service = new DefaultPriceMarketDataService(okxApiClient);

		assertThrows(IllegalArgumentException.class, () -> service.getOrderBook("binance", "BTC-USDT", 20));
	}
}
