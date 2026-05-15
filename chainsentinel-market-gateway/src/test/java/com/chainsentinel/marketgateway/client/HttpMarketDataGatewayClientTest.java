package com.chainsentinel.marketgateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.chainsentinel.marketgateway.config.MarketDataGatewayProperties;
import com.chainsentinel.price.api.dto.PriceHistoryCandle;
import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class HttpMarketDataGatewayClientTest {

	@Test
	void shouldSupportConfiguredProviderAndAliasesOnly() {
		MarketDataGatewayProperties properties = properties();
		HttpMarketDataGatewayClient client = new HttpMarketDataGatewayClient(properties, new RestTemplate());

		assertEquals("market_gateway", client.provider());
		assertTrue(client.supportsProvider("market_gateway"));
		assertTrue(client.supportsProvider("gateway"));
		assertFalse(client.supportsProvider("okx"));
		assertFalse(client.supportsProvider(null));
	}

	@Test
	void shouldFetchQuoteFromGateway() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("http://localhost:18080/api/v1/market/quotes/latest?provider=market_gateway&instType=SPOT&symbol=BTC&quoteSymbol=USDT&instId=BTC-USDT"))
			.andRespond(withSuccess("""
				{"provider":"akshare","baseSymbol":"BTC","quoteSymbol":"USDT","price":70000.1,"ts":1711910400000,"stale":false}
				""", MediaType.APPLICATION_JSON));
		HttpMarketDataGatewayClient client = new HttpMarketDataGatewayClient(properties(), restTemplate);

		Optional<PriceQuote> quote = client.getQuote(new PriceQuery("ETH", PriceInstType.SPOT, "BTC", "USDT", null));

		assertTrue(quote.isPresent());
		assertEquals("BTC", quote.get().baseSymbol());
		assertEquals("USDT", quote.get().quoteSymbol());
		assertEquals("70000.1", quote.get().price().toPlainString());
		assertEquals("akshare", quote.get().source());
		server.verify();
	}

	@Test
	void shouldFetchOrderBookFromGateway() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("http://localhost:18080/api/v1/market/order-book?provider=market_gateway&instId=BTC-USDT&depth=20"))
			.andRespond(withSuccess("""
				{"provider":"okx","instId":"BTC-USDT","ts":1711910400000,"seqId":101,"checksum":12345,
				 "asks":[{"price":70001.0,"size":1.1,"orderCount":2}],
				 "bids":[{"price":70000.0,"size":2.2,"orderCount":3}]}
				""", MediaType.APPLICATION_JSON));
		HttpMarketDataGatewayClient client = new HttpMarketDataGatewayClient(properties(), restTemplate);

		Optional<PriceOrderBook> orderBook = client.getOrderBook("BTC-USDT", 20);

		assertTrue(orderBook.isPresent());
		assertEquals("okx", orderBook.get().provider());
		assertEquals(101L, orderBook.get().seqId());
		assertEquals("70001.0", orderBook.get().asks().get(0).price().toPlainString());
		assertEquals("70000.0", orderBook.get().bids().get(0).price().toPlainString());
		server.verify();
	}

	@Test
	void shouldFetchTradesAndCandlesFromGateway() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("http://localhost:18080/api/v1/market/trades/recent?provider=market_gateway&instId=BTC-USDT&limit=2"))
			.andRespond(withSuccess("""
				{"data":[{"provider":"okx","instId":"BTC-USDT","tradeId":"1","price":70000.1,"size":0.01,"side":"buy","ts":1711910400000}]}
				""", MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://localhost:18080/api/v1/market/klines?provider=market_gateway&instId=BTC-USDT&bar=1m&limit=2&after=1711910400000"))
			.andRespond(withSuccess("""
				{"data":[{"ts":1711910340000,"closePrice":69999.9},{"ts":1711910400000,"closePrice":70000.1}]}
				""", MediaType.APPLICATION_JSON));
		HttpMarketDataGatewayClient client = new HttpMarketDataGatewayClient(properties(), restTemplate);

		List<PricePublicTrade> trades = client.getRecentPublicTrades("BTC-USDT", 2);
		List<PriceHistoryCandle> candles = client.getHistoryCandles("BTC-USDT", "1m", 1711910400000L, 2);

		assertEquals(1, trades.size());
		assertEquals("1", trades.get(0).tradeId());
		assertEquals(2, candles.size());
		assertEquals("69999.9", candles.get(0).closePrice().toPlainString());
		assertEquals("70000.1", candles.get(1).closePrice().toPlainString());
		server.verify();
	}

	@Test
	void shouldSendInternalTokenWhenConfigured() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("http://localhost:18080/api/v1/market/quotes/latest?provider=market_gateway&instType=SPOT&symbol=BTC&quoteSymbol=USDT&instId=BTC-USDT"))
			.andExpect(header("X-Internal-Token", "secret"))
			.andRespond(withSuccess("""
				{"provider":"okx","baseSymbol":"BTC","quoteSymbol":"USDT","price":70000.1,"ts":1711910400000,"stale":false}
				""", MediaType.APPLICATION_JSON));
		MarketDataGatewayProperties properties = properties();
		properties.setInternalToken("secret");
		HttpMarketDataGatewayClient client = new HttpMarketDataGatewayClient(properties, restTemplate);

		Optional<PriceQuote> quote = client.getQuote(new PriceQuery("ETH", PriceInstType.SPOT, "BTC", "USDT", null));

		assertTrue(quote.isPresent());
		server.verify();
	}

	private MarketDataGatewayProperties properties() {
		MarketDataGatewayProperties properties = new MarketDataGatewayProperties();
		properties.setBaseUrl("http://localhost:18080");
		properties.setProviderName("market_gateway");
		properties.setAliases(List.of("market_gateway", "gateway"));
		return properties;
	}
}
