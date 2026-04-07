package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.projection.AlertFailureSummaryProjection;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertFailureSummaryServiceTest {

	@Mock
	private AlertEventRepository alertEventRepository;

	@Test
	void shouldBuildFailureSummaryWithSortedCounts() {
		AlertFailureSummaryService service = new AlertFailureSummaryService(alertEventRepository);

		when(alertEventRepository.summarizeFailuresSince(any(Instant.class)))
			.thenReturn(
				List.of(new Row("FAILED", "HTTP 500", 3L), new Row("PENDING", null, 9L)),
				List.of(new Row("FAILED", "timeout", 11L))
			);

		AlertFailureSummaryService.AlertFailureSummaryView summary = service.summarize();

		assertEquals(2, summary.last24h().size());
		assertEquals("PENDING", summary.last24h().get(0).sendStatus());
		assertEquals("(none)", summary.last24h().get(0).lastError());
		assertEquals(9L, summary.last24h().get(0).count());
		assertEquals("FAILED", summary.last7d().get(0).sendStatus());
		assertEquals("timeout", summary.last7d().get(0).lastError());
		assertEquals(11L, summary.last7d().get(0).count());
	}

	private static final class Row implements AlertFailureSummaryProjection {
		private final String sendStatus;
		private final String lastError;
		private final Long failureCount;

		private Row(String sendStatus, String lastError, Long failureCount) {
			this.sendStatus = sendStatus;
			this.lastError = lastError;
			this.failureCount = failureCount;
		}

		@Override
		public String getSendStatus() {
			return sendStatus;
		}

		@Override
		public String getLastError() {
			return lastError;
		}

		@Override
		public Long getFailureCount() {
			return failureCount;
		}
	}
}
