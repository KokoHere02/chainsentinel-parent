package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.AlertDispatchService;
import com.chainsentinel.infra.service.AlertFailureSummaryService;
import com.chainsentinel.infra.service.AlertRetryService;
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
class InternalAlertControllerTest {

	@Mock
	private AlertFailureSummaryService alertFailureSummaryService;

	@Mock
	private AlertDispatchService alertDispatchService;

	@Mock
	private AlertRetryService alertRetryService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		InternalAlertController controller = new InternalAlertController(
			alertFailureSummaryService,
			alertDispatchService,
			alertRetryService
		);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldReturnFailureSummary() throws Exception {
		when(alertFailureSummaryService.summarize())
			.thenReturn(new AlertFailureSummaryService.AlertFailureSummaryView(
				List.of(new AlertFailureSummaryService.FailureItem("FAILED", "HTTP 500", 12L)),
				List.of(new AlertFailureSummaryService.FailureItem("PENDING", "(none)", 20L)),
				Instant.parse("2026-04-07T12:00:00Z")
			));

		mockMvc.perform(get("/api/internal/alerts/failure-summary"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.last24h[0].sendStatus", is("FAILED")))
			.andExpect(jsonPath("$.last24h[0].count", is(12)))
			.andExpect(jsonPath("$.last7d[0].sendStatus", is("PENDING")))
			.andExpect(jsonPath("$.last7d[0].count", is(20)));

		verify(alertFailureSummaryService).summarize();
	}

	@Test
	void shouldReturnLastFailure() throws Exception {
		when(alertFailureSummaryService.lastFailure())
			.thenReturn(new AlertFailureSummaryService.LastFailureView(
				true,
				88L,
				"HTTP 502",
				Instant.parse("2026-04-08T01:02:03Z")
			));

		mockMvc.perform(get("/api/internal/alerts/last-failure"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.exists", is(true)))
			.andExpect(jsonPath("$.alertId", is(88)))
			.andExpect(jsonPath("$.lastError", is("HTTP 502")));

		verify(alertFailureSummaryService).lastFailure();
	}

	@Test
	void shouldRetryOneAlert() throws Exception {
		when(alertDispatchService.retryOne(9L)).thenReturn(true);

		mockMvc.perform(post("/api/internal/alerts/9/retry"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.alertId", is(9)))
			.andExpect(jsonPath("$.success", is(true)));

		verify(alertDispatchService).retryOne(9L);
	}

	@Test
	void shouldRetryFailedInBatch() throws Exception {
		when(alertRetryService.retryFailed(2)).thenReturn(
			new AlertRetryService.BatchRetryResult(2, 1, 1, 0, List.of(101L), Instant.parse("2026-04-08T06:00:00Z"))
		);

		mockMvc.perform(post("/api/internal/alerts/retry-failed?limit=2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total", is(2)))
			.andExpect(jsonPath("$.success", is(1)))
			.andExpect(jsonPath("$.failed", is(1)))
			.andExpect(jsonPath("$.failedAlertIds[0]", is(101)));

		verify(alertRetryService).retryFailed(2);
	}

	@Test
	void shouldReturnBadRequestWhenRetryFailedLimitInvalid() throws Exception {
		mockMvc.perform(post("/api/internal/alerts/retry-failed?limit=0"))
			.andExpect(status().isBadRequest());
	}
}