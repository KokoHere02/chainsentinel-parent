package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.infra.service.DashboardQueryService;
import com.chainsentinel.infra.service.DbPriceTickBatchWriter;
import com.chainsentinel.infra.service.OkxBackfillAsyncTaskService;
import java.math.BigDecimal;
import java.time.Duration;
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
class DashboardControllerTest {

	@Mock
	private DashboardQueryService dashboardQueryService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		DashboardController controller = new DashboardController(dashboardQueryService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldReturnOverview() throws Exception {
		when(dashboardQueryService.overview()).thenReturn(new DashboardQueryService.OverviewView(
			10L,
			6L,
			20L,
			5L,
			3L,
			1L
		));

		mockMvc.perform(get("/api/dashboard/overview"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.monitorAddressCount", is(10)))
			.andExpect(jsonPath("$.todayAlertCount", is(20)))
			.andExpect(jsonPath("$.runningBackfillCount", is(1)));
	}

	@Test
	void shouldReturnPriceSummary() throws Exception {
		when(dashboardQueryService.priceSummary(eq(Duration.ofHours(24)), eq(2))).thenReturn(List.of(
			new DashboardQueryService.PriceSummaryView(
				"BTC-USDT",
				new BigDecimal("50000"),
				new BigDecimal("49000"),
				new BigDecimal("2.040816"),
				1700000000000L
			)
		));

		mockMvc.perform(get("/api/dashboard/price/summary").param("window", "24h").param("limit", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].instId", is("BTC-USDT")));

		verify(dashboardQueryService).priceSummary(Duration.ofHours(24), 2);
	}

	@Test
	void shouldReturnBadRequestWhenPriceSummaryLimitInvalid() throws Exception {
		mockMvc.perform(get("/api/dashboard/price/summary").param("limit", "0"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void shouldReturnBadRequestWhenPriceTrendBucketInvalid() throws Exception {
		mockMvc.perform(get("/api/dashboard/price/trend")
				.param("instId", "BTC-USDT")
				.param("bucketMs", "999"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void shouldReturnBackfillTasks() throws Exception {
		when(dashboardQueryService.backfillTasks(eq(0), eq(20), eq("RUNNING"), eq("BTC-USDT"))).thenReturn(List.of(
			new OkxBackfillAsyncTaskService.TaskStatus(
				"okx-1-1",
				"RUNNING",
				"BTC-USDT",
				1700000000000L,
				1700086400000L,
				"1m",
				300,
				1000,
				50L,
				Instant.parse("2026-04-26T10:00:00Z"),
				Instant.parse("2026-04-26T10:00:01Z"),
				null,
				null,
				null
			)
		));

		mockMvc.perform(get("/api/dashboard/backfill/tasks")
				.param("status", "RUNNING")
				.param("instId", "BTC-USDT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].taskId", is("okx-1-1")))
			.andExpect(jsonPath("$[0].status", is("RUNNING")));
	}

	@Test
	void shouldReturnDashboardHealth() throws Exception {
		when(dashboardQueryService.health()).thenReturn(new DashboardQueryService.DashboardHealthView(
			1L,
			1L,
			1L,
			0L,
			2L,
			new DbPriceTickBatchWriter.TickIngestStatus(true, 200, 20000, 1000L, 200, 0.0D, 0.06D, 12, false),
			new DashboardQueryService.TickIngestHealthView("HEALTHY", List.of("ok")),
			List.of()
		));

		mockMvc.perform(get("/api/dashboard/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tickIngest.batchSize", is(200)))
			.andExpect(jsonPath("$.tickIngest.highWatermark", is(200)))
			.andExpect(jsonPath("$.tickIngest.queueSize", is(12)))
			.andExpect(jsonPath("$.tickIngestHealth.status", is("HEALTHY")));
	}
}
