package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.PricePullTargetService;
import com.chainsentinel.core.service.dto.PricePullTargetView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PricePullTargetControllerTest {

	@Mock
	private PricePullTargetService pricePullTargetService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		PricePullTargetController controller = new PricePullTargetController(pricePullTargetService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldCreate() throws Exception {
		when(pricePullTargetService.create(any())).thenReturn(new PricePullTargetView(
			1L, 1L, 1L, "SPOT", "BTC-USDT", "USDT", true, 1000, 1
		));

		mockMvc.perform(post("/api/price-pull-targets")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  \"assetId\": 1,
					  \"providerConfigId\": 1,
					  \"instType\": \"SPOT\",
					  \"instId\": \"BTC-USDT\",
					  \"quoteSymbol\": \"USDT\",
					  \"enabled\": true,
					  \"pollIntervalMs\": 1000,
					  \"priority\": 1
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id", is(1)))
			.andExpect(jsonPath("$.instId", is("BTC-USDT")));
	}

	@Test
	void shouldReturnBadRequestWhenDuplicateTarget() throws Exception {
		when(pricePullTargetService.create(any())).thenThrow(new IllegalArgumentException(
			"price pull target already exists: providerConfigId=1, instType=SPOT, instId=BTC-USDT"
		));

		mockMvc.perform(post("/api/price-pull-targets")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  \"assetId\": 1,
					  \"providerConfigId\": 1,
					  \"instType\": \"SPOT\",
					  \"instId\": \"BTC-USDT\",
					  \"quoteSymbol\": \"USDT\",
					  \"enabled\": true,
					  \"priority\": 1
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void shouldList() throws Exception {
		when(pricePullTargetService.list(eq(1L), eq(true), eq("btc"), eq(20))).thenReturn(List.of(
			new PricePullTargetView(1L, 1L, 1L, "SPOT", "BTC-USDT", "USDT", true, null, 1)
		));

		mockMvc.perform(get("/api/price-pull-targets")
				.param("providerConfigId", "1")
				.param("enabled", "true")
				.param("q", "btc")
				.param("limit", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].instId", is("BTC-USDT")));
	}

	@Test
	void shouldEnable() throws Exception {
		when(pricePullTargetService.setEnabled(1L, true)).thenReturn(
			new PricePullTargetView(1L, 1L, 1L, "SPOT", "BTC-USDT", "USDT", true, null, 1)
		);

		mockMvc.perform(patch("/api/price-pull-targets/1/enable"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled", is(true)));
	}

	@Test
	void shouldDisable() throws Exception {
		when(pricePullTargetService.setEnabled(1L, false)).thenReturn(
			new PricePullTargetView(1L, 1L, 1L, "SPOT", "BTC-USDT", "USDT", false, null, 1)
		);

		mockMvc.perform(patch("/api/price-pull-targets/1/disable"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled", is(false)));
	}

	@Test
	void shouldDelete() throws Exception {
		mockMvc.perform(delete("/api/price-pull-targets/1"))
			.andExpect(status().isOk());
		verify(pricePullTargetService).delete(1L);
	}
}