package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.price.api.PriceMarketDataService;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PriceOrderBookLevel;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PriceMarketControllerTest {

	@Mock
	private PriceMarketDataService priceMarketDataService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new PriceMarketController(priceMarketDataService)).build();
	}

	@Test
	void shouldReturnDepth() throws Exception {
		when(priceMarketDataService.getOrderBook(eq("okx"), eq("BTC-USDT"), eq(20))).thenReturn(
			new PriceOrderBook(
				"okx",
				"BTC-USDT",
				1700000000000L,
				1001L,
				12345L,
				List.of(new PriceOrderBookLevel(new BigDecimal("70100.1"), new BigDecimal("1.25"), 3)),
				List.of(new PriceOrderBookLevel(new BigDecimal("70100.0"), new BigDecimal("0.80"), 2))
			)
		);

		mockMvc.perform(get("/api/prices/market/depth")
				.param("instId", "BTC-USDT")
				.param("depth", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.provider", is("okx")))
			.andExpect(jsonPath("$.asks", hasSize(1)))
			.andExpect(jsonPath("$.asks[0].price", is(70100.1)));

		verify(priceMarketDataService).getOrderBook("okx", "BTC-USDT", 20);
	}

	@Test
	void shouldReturnRecentTrades() throws Exception {
		when(priceMarketDataService.getRecentPublicTrades(eq("okx"), eq("BTC-USDT"), eq(10))).thenReturn(List.of(
			new PricePublicTrade("okx", "BTC-USDT", "1001", new BigDecimal("70100.1"), new BigDecimal("0.01"), "buy", 1700000000000L)
		));

		mockMvc.perform(get("/api/prices/market/trades")
				.param("instId", "BTC-USDT")
				.param("limit", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].tradeId", is("1001")))
			.andExpect(jsonPath("$[0].side", is("buy")));

		verify(priceMarketDataService).getRecentPublicTrades("okx", "BTC-USDT", 10);
	}

	@Test
	void shouldRejectInvalidDepth() throws Exception {
		mockMvc.perform(get("/api/prices/market/depth")
				.param("instId", "BTC-USDT")
				.param("depth", "401"))
			.andExpect(status().isBadRequest());
	}
}
