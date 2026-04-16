package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.infra.service.PriceTickQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PriceHistoryControllerTest {

	@Mock
	private PriceTickQueryService priceTickQueryService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		PriceHistoryController controller = new PriceHistoryController(priceTickQueryService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldQueryHistoryTicks() throws Exception {
		PriceTickQueryService.PriceTickView view = new PriceTickQueryService.PriceTickView(
			1L,
			"okx_ws",
			"SPOT",
			"BTC-USDT",
			"BTC",
			"USDT",
			new BigDecimal("70000.12"),
			1700000000000L,
			Instant.parse("2026-04-10T10:00:00Z")
		);
		when(priceTickQueryService.query(eq("okx_ws"), eq("BTC-USDT"), eq(1700000000000L), eq(1700003600000L), eq(200)))
			.thenReturn(List.of(view));

		mockMvc.perform(get("/api/prices/history")
				.param("provider", "okx_ws")
				.param("instId", "BTC-USDT")
				.param("from", "1700000000000")
				.param("to", "1700003600000")
				.param("limit", "200"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].instId", is("BTC-USDT")))
			.andExpect(jsonPath("$[0].price", is(70000.12)));

		verify(priceTickQueryService).query("okx_ws", "BTC-USDT", 1700000000000L, 1700003600000L, 200);
	}

	@Test
	void shouldReturnBadRequestWhenHistoryRangeInvalid() throws Exception {
		mockMvc.perform(get("/api/prices/history")
				.param("instId", "BTC-USDT")
				.param("from", "10")
				.param("to", "1"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void shouldAggregateHistoryTicks() throws Exception {
		PriceTickQueryService.PriceTickAggregateView view = new PriceTickQueryService.PriceTickAggregateView(
			1700000000000L,
			new BigDecimal("70100.1"),
			new BigDecimal("70000.1"),
			new BigDecimal("70200.1"),
			12L
		);
		when(priceTickQueryService.aggregate(eq("okx_ws"), eq("BTC-USDT"), eq(1700000000000L), eq(1700003600000L), eq(60000L), eq(1000)))
			.thenReturn(List.of(view));

		mockMvc.perform(get("/api/prices/history/aggregate")
				.param("provider", "okx_ws")
				.param("instId", "BTC-USDT")
				.param("from", "1700000000000")
				.param("to", "1700003600000")
				.param("bucketMs", "60000")
				.param("limit", "1000"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].bucketStartTs", is(1700000000000L)))
			.andExpect(jsonPath("$[0].last", is(70100.1)))
			.andExpect(jsonPath("$[0].count", is(12)));

		verify(priceTickQueryService).aggregate("okx_ws", "BTC-USDT", 1700000000000L, 1700003600000L, 60000L, 1000);
	}

	@Test
	void shouldReturnBadRequestWhenBucketInvalid() throws Exception {
		mockMvc.perform(get("/api/prices/history/aggregate")
				.param("instId", "BTC-USDT")
				.param("bucketMs", "500"))
			.andExpect(status().isBadRequest());
	}
}