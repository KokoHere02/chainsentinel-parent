package com.chainsentinel.infra.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.projection.AlertFailureSummaryProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

	@Test
	void shouldReturnLastFailureWhenExists() {
		AlertFailureSummaryService service = new AlertFailureSummaryService(alertEventRepository);

		AlertEventEntity entity = new AlertEventEntity();
		ReflectionTestUtils.setField(entity, "id", 77L);
		entity.setSendStatus("FAILED");
		entity.setLastError("timeout");
		entity.setCreatedAt(Instant.parse("2026-04-08T02:00:00Z"));

		when(alertEventRepository.findTopBySendStatusNotOrderByCreatedAtDesc("SENT"))
			.thenReturn(Optional.of(entity));

		AlertFailureSummaryService.LastFailureView view = service.lastFailure();

		assertEquals(true, view.exists());
		assertEquals(77L, view.alertId());
		assertEquals("timeout", view.lastError());
		assertEquals(Instant.parse("2026-04-08T02:00:00Z"), view.lastFailedAt());
	}

	@Test
	void shouldReturnEmptyWhenNoFailureExists() {
		AlertFailureSummaryService service = new AlertFailureSummaryService(alertEventRepository);

		when(alertEventRepository.findTopBySendStatusNotOrderByCreatedAtDesc("SENT"))
			.thenReturn(Optional.empty());

		AlertFailureSummaryService.LastFailureView view = service.lastFailure();

		assertEquals(false, view.exists());
		assertEquals(null, view.alertId());
		assertEquals(null, view.lastError());
		assertEquals(null, view.lastFailedAt());
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
