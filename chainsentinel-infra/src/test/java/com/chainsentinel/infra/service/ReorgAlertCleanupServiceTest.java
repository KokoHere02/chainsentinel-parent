package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReorgAlertCleanupServiceTest {

	@Mock
	private AlertEventRepository alertEventRepository;

	private ReorgAlertCleanupService service;
	private SimpleMeterRegistry meterRegistry;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		service = new ReorgAlertCleanupService(alertEventRepository, meterRegistry);
	}

	@Test
	void shouldCancelPendingAndFailedAlertsForReorgedEvents() {
		AlertEventEntity pending = alert(11L, 101L, "PENDING");
		AlertEventEntity failed = alert(12L, 101L, "FAILED");
		when(alertEventRepository.findByAssetEventIdAndSendStatusIn(eq(101L), eq(List.of("PENDING", "FAILED"))))
			.thenReturn(List.of(pending, failed));

		int canceled = service.cancelPendingAlertsForReorgedEvents(List.of(101L));

		assertEquals(2, canceled);
		ArgumentCaptor<List<AlertEventEntity>> captor = ArgumentCaptor.forClass(List.class);
		verify(alertEventRepository).saveAll(captor.capture());
		assertEquals("CANCELED", captor.getValue().get(0).getSendStatus());
		assertEquals("asset event reorged", captor.getValue().get(0).getLastError());
		assertEquals("CANCELED", captor.getValue().get(1).getSendStatus());
		assertEquals(2.0, meterRegistry.get("alert_reorg_cleanup_total").counter().count());
	}

	@Test
	void shouldSkipWhenAssetEventIdsEmpty() {
		int canceled = service.cancelPendingAlertsForReorgedEvents(List.of());

		assertEquals(0, canceled);
		verify(alertEventRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
	}

	private static AlertEventEntity alert(Long id, Long assetEventId, String status) {
		AlertEventEntity entity = new AlertEventEntity();
		ReflectionTestUtils.setField(entity, "id", id);
		entity.setAssetEventId(assetEventId);
		entity.setSendStatus(status);
		entity.setRetryCount(0);
		entity.setSeverity("HIGH");
		return entity;
	}
}
