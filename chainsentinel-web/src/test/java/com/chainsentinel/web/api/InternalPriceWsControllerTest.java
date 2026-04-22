package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.stream.PriceStreamManager;
import com.chainsentinel.price.stream.PriceStreamProviderStatus;
import com.chainsentinel.price.stream.PriceStreamStatusService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InternalPriceWsControllerTest {

	@Mock
	private PriceStreamStatusService priceStreamStatusService;

	@Mock
	private PriceStreamManager priceStreamManager;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		InternalPriceWsController controller = new InternalPriceWsController(priceStreamStatusService, priceStreamManager);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldReturnPriceWsStatuses() throws Exception {
		when(priceStreamStatusService.listStatuses()).thenReturn(List.of(
			new PriceStreamProviderStatus(
				"okx_ws",
				true,
				false,
				true,
				4,
				"error",
				Instant.parse("2026-04-15T08:00:00Z"),
				"HttpConnectTimeoutException",
				"HTTP connect timed out",
				Instant.parse("2026-04-15T08:00:01Z"),
				2,
				Instant.parse("2026-04-15T08:00:02Z"),
				2
			)
		));

		mockMvc.perform(get("/api/internal/price-ws/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].provider", is("okx_ws")))
			.andExpect(jsonPath("$[0].reconnectAttempts", is(4)))
			.andExpect(jsonPath("$[0].lastReconnectReason", is("error")))
			.andExpect(jsonPath("$[0].lastErrorType", is("HttpConnectTimeoutException")));
	}

	@Test
	void shouldReturnCurrentSubscriptions() throws Exception {
		when(priceStreamManager.currentEffectiveSubscriptions()).thenReturn(Map.of(
			"okx_ws",
			List.of(
				new PriceQuery("OFFCHAIN", PriceInstType.SPOT, "BTC", "USDT", null),
				new PriceQuery("OFFCHAIN", PriceInstType.SWAP, "ETH", "USDT", null)
			)
		));

		mockMvc.perform(get("/api/internal/price-ws/subscriptions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].provider", is("okx_ws")))
			.andExpect(jsonPath("$[0].queries", hasSize(2)))
			.andExpect(jsonPath("$[0].queries[0].instType", is("SPOT")))
			.andExpect(jsonPath("$[0].queries[0].instId", is("BTC-USDT")))
			.andExpect(jsonPath("$[0].queries[1].instType", is("SWAP")))
			.andExpect(jsonPath("$[0].queries[1].instId", is("ETH-USDT")));
	}
}
