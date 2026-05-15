package com.chainsentinel.marketgateway.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.marketgateway.provider.MarketDataProvider;
import com.chainsentinel.marketgateway.provider.MarketDataProviderDescriptor;
import com.chainsentinel.marketgateway.provider.MarketDataProviderRouter;
import com.chainsentinel.marketgateway.provider.MarketDataCapability;
import com.chainsentinel.marketgateway.provider.MarketDataProviderStatus;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MarketGatewayControllerTest {

	private final MarketDataProvider provider = org.mockito.Mockito.mock(MarketDataProvider.class);
	private final MarketDataProviderRouter router = org.mockito.Mockito.mock(MarketDataProviderRouter.class);
	private final MockMvc mockMvc = MockMvcBuilders
		.standaloneSetup(new MarketGatewayController(router))
		.setControllerAdvice(new MarketGatewayExceptionHandler())
		.build();

	@Test
	void shouldReturnHealthAndProviderStatus() throws Exception {
		when(router.descriptors()).thenReturn(List.of(new MarketDataProviderDescriptor(
			"noop",
			MarketDataProviderStatus.DEGRADED,
			List.of(MarketDataCapability.QUOTE, MarketDataCapability.KLINES),
			"contract only"
		)));

		mockMvc.perform(get("/api/v1/market/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DEGRADED"))
			.andExpect(jsonPath("$.providers[0].provider").value("noop"))
			.andExpect(jsonPath("$.providers[0].capabilities[0]").value("QUOTE"));
	}

	@Test
	void shouldReturnProviders() throws Exception {
		when(router.descriptors()).thenReturn(List.of(new MarketDataProviderDescriptor(
			"noop",
			MarketDataProviderStatus.DEGRADED,
			List.of(MarketDataCapability.ORDER_BOOK),
			"contract only"
		)));

		mockMvc.perform(get("/api/v1/market/providers"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.providers[0].provider").value("noop"))
			.andExpect(jsonPath("$.providers[0].status").value("DEGRADED"))
			.andExpect(jsonPath("$.providers[0].capabilities[0]").value("ORDER_BOOK"));
	}

	@Test
	void shouldReturnLatestQuote() throws Exception {
		when(router.resolve("noop")).thenReturn(provider);
		when(provider.getQuote(any(PriceQuery.class))).thenReturn(Optional.of(
			new PriceQuote("BTC", "USDT", new BigDecimal("70000.1"), 1711910400000L, "noop", false)
		));

		mockMvc.perform(get("/api/v1/market/quotes/latest")
				.param("provider", "noop")
				.param("instType", "SPOT")
				.param("symbol", "BTC")
				.param("quoteSymbol", "USDT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.baseSymbol").value("BTC"))
			.andExpect(jsonPath("$.quoteSymbol").value("USDT"))
			.andExpect(jsonPath("$.price").value(70000.1))
			.andExpect(jsonPath("$.source").value("noop"));
	}

	@Test
	void shouldReturnLatestQuoteFromDefaultProvider() throws Exception {
		when(router.resolve(null)).thenReturn(provider);
		when(provider.getQuote(any(PriceQuery.class))).thenReturn(Optional.of(
			new PriceQuote("BTC", "USDT", new BigDecimal("70000.1"), 1711910400000L, "okx", false)
		));

		mockMvc.perform(get("/api/v1/market/quotes/latest")
				.param("instType", "SPOT")
				.param("symbol", "BTC")
				.param("quoteSymbol", "USDT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("okx"));
	}

	@Test
	void shouldReturnNotFoundWhenQuoteUnavailable() throws Exception {
		when(router.resolve("noop")).thenReturn(provider);
		when(provider.getQuote(any(PriceQuery.class))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/market/quotes/latest")
				.param("provider", "noop")
				.param("symbol", "BTC")
				.param("quoteSymbol", "USDT"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void shouldReturnOrderBook() throws Exception {
		when(router.resolve("noop")).thenReturn(provider);
		when(provider.getOrderBook(eq("BTC-USDT"), eq(20))).thenReturn(Optional.of(
			new PriceOrderBook("noop", "BTC-USDT", 1711910400000L, 101L, 12345L, List.of(), List.of())
		));

		mockMvc.perform(get("/api/v1/market/order-book")
				.param("provider", "noop")
				.param("instId", "BTC-USDT")
				.param("depth", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.provider").value("noop"))
			.andExpect(jsonPath("$.instId").value("BTC-USDT"))
			.andExpect(jsonPath("$.seqId").value(101));
	}

	@Test
	void shouldReturnTradesEnvelope() throws Exception {
		when(router.resolve("noop")).thenReturn(provider);
		when(provider.getRecentPublicTrades("BTC-USDT", 2)).thenReturn(List.of(
			new PricePublicTrade("noop", "BTC-USDT", "1", new BigDecimal("70000.1"), new BigDecimal("0.01"), "buy", 1711910400000L)
		));

		mockMvc.perform(get("/api/v1/market/trades/recent")
				.param("provider", "noop")
				.param("instId", "BTC-USDT")
				.param("limit", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].tradeId").value("1"))
			.andExpect(jsonPath("$.data[0].side").value("buy"));
	}

	@Test
	void shouldRejectInvalidDepth() throws Exception {
		mockMvc.perform(get("/api/v1/market/order-book")
				.param("provider", "noop")
				.param("instId", "BTC-USDT")
				.param("depth", "0"))
			.andExpect(status().isBadRequest());
	}
}
