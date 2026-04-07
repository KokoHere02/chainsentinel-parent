package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.infra.service.AlertFailureSummaryService;
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

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		InternalAlertController controller = new InternalAlertController(alertFailureSummaryService);
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
}
