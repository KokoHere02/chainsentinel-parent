package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.infra.service.OkxBackfillAsyncTaskService;
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
	private OkxBackfillAsyncTaskService okxBackfillAsyncTaskService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		InternalPriceTickBackfillController controller = new InternalPriceTickBackfillController(okxBackfillAsyncTaskService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldSubmitBackfillOkxTask() throws Exception {
		when(okxBackfillAsyncTaskService.submit(
			eq("BTC-USDT"),
			eq(1700000000000L),
			eq(1700086400000L),
			eq("1m"),
			eq(300),
			eq(200),
			eq(120L)
		)).thenReturn(new OkxBackfillAsyncTaskService.TaskAccepted(
			"okx-1-1",
			"QUEUED",
			"BTC-USDT",
			1700000000000L,
			1700086400000L,
			"1m",
			300,
			200,
			120L,
			Instant.parse("2026-04-17T10:00:00Z")
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
			.andExpect(jsonPath("$.taskId", is("okx-1-1")))
			.andExpect(jsonPath("$.status", is("QUEUED")))
			.andExpect(jsonPath("$.instId", is("BTC-USDT")));
	}

	@Test
	void shouldQueryTaskStatus() throws Exception {
		when(okxBackfillAsyncTaskService.query(eq("okx-1-1"))).thenReturn(new OkxBackfillAsyncTaskService.TaskStatus(
			"okx-1-1",
			"SUCCEEDED",
			"BTC-USDT",
			1700000000000L,
			1700086400000L,
			"1m",
			300,
			200,
			120L,
			Instant.parse("2026-04-17T10:00:00Z"),
			Instant.parse("2026-04-17T10:00:01Z"),
			Instant.parse("2026-04-17T10:00:05Z"),
			null,
			null
		));

		mockMvc.perform(get("/api/internal/price-ticks/backfill/okx/tasks/okx-1-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taskId", is("okx-1-1")))
			.andExpect(jsonPath("$.status", is("SUCCEEDED")));
	}

	@Test
	void shouldReturnNotFoundWhenTaskMissing() throws Exception {
		when(okxBackfillAsyncTaskService.query(eq("missing"))).thenReturn(null);

		mockMvc.perform(get("/api/internal/price-ticks/backfill/okx/tasks/missing"))
			.andExpect(status().isNotFound());
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
