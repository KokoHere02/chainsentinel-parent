package com.chainsentinel.marketgateway.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.price.api.dto.PriceHistoryCandle;
import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import com.chainsentinel.price.provider.okx.OkxPublicMarketDataClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OkxMarketDataProviderTest {

	@Mock
	private OkxPublicMarketDataClient okxPublicMarketDataClient;

	@Test
	void shouldExposeOkxProviderDescriptor() {
		OkxMarketDataProvider provider = new OkxMarketDataProvider(okxPublicMarketDataClient);

		assertEquals("okx", provider.provider());
		assertTrue(provider.supportsProvider("okx"));
		assertTrue(provider.supportsProvider("okx_ws"));
		assertFalse(provider.supportsProvider(null));
		assertEquals(MarketDataProviderStatus.UP, provider.descriptor().status());
		assertTrue(provider.descriptor().capabilities().contains(MarketDataCapability.QUOTE));
	}

	@Test
	void shouldDelegateMarketDataCalls() {
		PriceQuery query = new PriceQuery(null, PriceInstType.SPOT, "BTC", "USDT", null);
		PriceQuote quote = new PriceQuote("BTC", "USDT", new BigDecimal("70000.1"), 1711910400000L, "okx", false);
		PriceOrderBook orderBook = new PriceOrderBook("okx", "BTC-USDT", null, null, null, List.of(), List.of());
		PricePublicTrade trade = new PricePublicTrade("okx", "BTC-USDT", "1", new BigDecimal("70000.1"), new BigDecimal("0.01"), "buy", 1711910400000L);
		PriceHistoryCandle candle = new PriceHistoryCandle(1711910400000L, new BigDecimal("70000.1"));
		when(okxPublicMarketDataClient.getQuote(query)).thenReturn(Optional.of(quote));
		when(okxPublicMarketDataClient.getOrderBook(eq("BTC-USDT"), eq(20))).thenReturn(Optional.of(orderBook));
		when(okxPublicMarketDataClient.getRecentPublicTrades(eq("BTC-USDT"), eq(10))).thenReturn(List.of(trade));
		when(okxPublicMarketDataClient.getHistoryCandles(eq("BTC-USDT"), eq("1m"), eq(1711910400000L), eq(100))).thenReturn(List.of(candle));

		OkxMarketDataProvider provider = new OkxMarketDataProvider(okxPublicMarketDataClient);

		assertSame(quote, provider.getQuote(query).orElseThrow());
		assertSame(orderBook, provider.getOrderBook("BTC-USDT", 20).orElseThrow());
		assertSame(trade, provider.getRecentPublicTrades("BTC-USDT", 10).get(0));
		assertSame(candle, provider.getHistoryCandles("BTC-USDT", "1m", 1711910400000L, 100).get(0));
		verify(okxPublicMarketDataClient).getQuote(query);
	}
}
