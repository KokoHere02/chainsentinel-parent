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
class InternalPriceTickControllerTest {

	@Mock
	private PriceTickQueryService priceTickQueryService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		InternalPriceTickController controller = new InternalPriceTickController(priceTickQueryService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldQueryPriceTicks() throws Exception {
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
		when(priceTickQueryService.query(eq("okx_ws"), eq("BTC-USDT"), eq(1700000000000L), eq(1700001000000L), eq(100)))
			.thenReturn(List.of(view));

		mockMvc.perform(get("/api/internal/price-ticks")
				.param("provider", "okx_ws")
				.param("instId", "BTC-USDT")
				.param("from", "1700000000000")
				.param("to", "1700001000000")
				.param("limit", "100"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].providerName", is("okx_ws")))
			.andExpect(jsonPath("$[0].instId", is("BTC-USDT")))
			.andExpect(jsonPath("$[0].price", is(70000.12)));

		verify(priceTickQueryService).query("okx_ws", "BTC-USDT", 1700000000000L, 1700001000000L, 100);
	}

	@Test
	void shouldReturnBadRequestWhenFromGreaterThanTo() throws Exception {
		mockMvc.perform(get("/api/internal/price-ticks")
				.param("from", "10")
				.param("to", "1"))
			.andExpect(status().isBadRequest());
	}
}

