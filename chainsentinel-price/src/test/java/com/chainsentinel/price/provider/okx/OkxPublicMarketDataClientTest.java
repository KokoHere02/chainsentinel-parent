package com.chainsentinel.price.provider.okx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.chainsentinel.price.api.dto.PriceHistoryCandle;
import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PriceOrderBookLevel;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import com.chainsentinel.price.provider.okx.dto.OkxHistoryCandle;
import com.chainsentinel.price.provider.okx.dto.OkxOrderBookResponse;
import com.chainsentinel.price.provider.okx.dto.OkxTickerResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OkxPublicMarketDataClientTest {

	@Mock
	private OkxApiClient okxApiClient;

	@Test
	void shouldConvertTickerResponseToPriceQuote() {
		OkxTickerResponse response = new OkxTickerResponse();
		response.setCode("0");
		OkxTickerResponse.OkxTickerData data = new OkxTickerResponse.OkxTickerData();
		data.setInstId("BTC-USDT");
		data.setLast("70000.1");
		data.setTs("1711910400000");
		response.setData(List.of(data));
		when(okxApiClient.fetchTicker("BTC-USDT")).thenReturn(Optional.of(response));

		OkxPublicMarketDataClient client = new OkxPublicMarketDataClient(okxApiClient);
		Optional<PriceQuote> quote = client.getQuote(new PriceQuery("ETH", PriceInstType.SPOT, "BTC", "USDT", null));

		assertTrue(quote.isPresent());
		assertEquals("BTC", quote.get().baseSymbol());
		assertEquals("USDT", quote.get().quoteSymbol());
		assertEquals("70000.1", quote.get().price().toPlainString());
		assertEquals(1711910400000L, quote.get().ts());
		assertEquals("okx", quote.get().source());
	}

	@Test
	void shouldConvertOrderBookResponseToPriceOrderBook() {
		when(okxApiClient.fetchOrderBook(eq("BTC-USDT"), eq(20))).thenReturn(Optional.of(
			new OkxOrderBookResponse(
				"BTC-USDT",
				1711910400000L,
				101L,
				12345L,
				List.of(new PriceOrderBookLevel(new BigDecimal("70001.0"), new BigDecimal("1.1"), 2)),
				List.of(new PriceOrderBookLevel(new BigDecimal("70000.0"), new BigDecimal("2.2"), 3))
			)
		));

		OkxPublicMarketDataClient client = new OkxPublicMarketDataClient(okxApiClient);
		Optional<PriceOrderBook> orderBook = client.getOrderBook("BTC-USDT", 20);

		assertTrue(orderBook.isPresent());
		assertEquals("okx", orderBook.get().provider());
		assertEquals("BTC-USDT", orderBook.get().instId());
		assertEquals(101L, orderBook.get().seqId());
		assertEquals("70001.0", orderBook.get().asks().get(0).price().toPlainString());
		assertEquals("70000.0", orderBook.get().bids().get(0).price().toPlainString());
	}

	@Test
	void shouldConvertHistoryCandles() {
		when(okxApiClient.fetchHistoryCandles(eq("BTC-USDT"), eq("1m"), eq(1711910400000L), eq(100))).thenReturn(List.of(
			new OkxHistoryCandle(1711910340000L, new BigDecimal("69999.9")),
			new OkxHistoryCandle(1711910400000L, new BigDecimal("70000.1"))
		));

		OkxPublicMarketDataClient client = new OkxPublicMarketDataClient(okxApiClient);
		List<PriceHistoryCandle> candles = client.getHistoryCandles("BTC-USDT", "1m", 1711910400000L, 100);

		assertEquals(2, candles.size());
		assertEquals(1711910340000L, candles.get(0).ts());
		assertEquals("69999.9", candles.get(0).closePrice().toPlainString());
		assertEquals(1711910400000L, candles.get(1).ts());
		assertEquals("70000.1", candles.get(1).closePrice().toPlainString());
	}
}
