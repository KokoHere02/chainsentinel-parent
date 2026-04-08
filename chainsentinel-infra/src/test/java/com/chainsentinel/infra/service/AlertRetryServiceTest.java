package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.AlertDispatchService;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AlertRetryServiceTest {

	@Mock
	private AlertEventRepository alertEventRepository;

	@Mock
	private AlertDispatchService alertDispatchService;

	private AlertRetryService alertRetryService;

	@BeforeEach
	void setUp() {
		alertRetryService = new AlertRetryService(alertEventRepository, alertDispatchService);
	}

	@Test
	void shouldRetryFailedInBatch() {
		AlertEventEntity a1 = new AlertEventEntity();
		ReflectionTestUtils.setField(a1, "id", 11L);
		AlertEventEntity a2 = new AlertEventEntity();
		ReflectionTestUtils.setField(a2, "id", 12L);
		AlertEventEntity a3 = new AlertEventEntity();
		ReflectionTestUtils.setField(a3, "id", 13L);

		when(alertEventRepository.findBySendStatusInOrderByIdAsc(eq(List.of("FAILED", "PENDING")), any()))
			.thenReturn(List.of(a1, a2, a3));
		when(alertDispatchService.retryOne(11L)).thenReturn(true);
		when(alertDispatchService.retryOne(12L)).thenReturn(false);
		when(alertDispatchService.retryOne(13L)).thenReturn(true);

		AlertRetryService.BatchRetryResult result = alertRetryService.retryFailed(100);

		assertEquals(3, result.total());
		assertEquals(2, result.success());
		assertEquals(1, result.failed());
		assertEquals(0, result.skipped());
		assertIterableEquals(List.of(12L), result.failedAlertIds());
		verify(alertDispatchService).retryOne(11L);
		verify(alertDispatchService).retryOne(12L);
		verify(alertDispatchService).retryOne(13L);
	}
}
