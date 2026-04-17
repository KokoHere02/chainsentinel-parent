package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.infra.service.OkxPriceTickBackfillService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InternalPriceTickBackfillControllerTest {

	@Mock
	private OkxPriceTickBackfillService okxPriceTickBackfillService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		InternalPriceTickBackfillController controller = new InternalPriceTickBackfillController(okxPriceTickBackfillService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldBackfillOkxTicks() throws Exception {
		when(okxPriceTickBackfillService.backfill(
			eq("BTC-USDT"),
			eq(1700000000000L),
			eq(1700086400000L),
			eq("1m"),
			eq(300),
			eq(200),
			eq(120L)
		)).thenReturn(new OkxPriceTickBackfillService.BackfillResult(
			"BTC-USDT",
			1700000000000L,
			1700086400000L,
			"1m",
			10,
			3000,
			2800,
			true,
			"reached_from",
			1699999999000L,
			1700086399000L,
			1699999998999L,
			Instant.parse("2026-04-17T10:00:00Z"),
			Instant.parse("2026-04-17T10:00:05Z")
		));

		mockMvc.perform(post("/api/internal/price-ticks/backfill/okx")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  \"instId\": \"BTC-USDT\",
					  \"fromTs\": 1700000000000,
					  \"toTs\": 1700086400000
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.instId", is("BTC-USDT")))
			.andExpect(jsonPath("$.inserted", is(2800)))
			.andExpect(jsonPath("$.reachedFrom", is(true)))
			.andExpect(jsonPath("$.stopReason", is("reached_from")));
	}

	@Test
	void shouldReturnBadRequestWhenRangeInvalid() throws Exception {
		mockMvc.perform(post("/api/internal/price-ticks/backfill/okx")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  \"instId\": \"BTC-USDT\",
					  \"fromTs\": 2,
					  \"toTs\": 1
					}
					"""))
			.andExpect(status().isBadRequest());
	}
}