package com.chainsentinel.web.api.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.service.DbPriceTickBatchWriter;
import com.chainsentinel.infra.service.OkxBackfillAsyncTaskService;
import com.chainsentinel.price.stream.PriceStreamProviderStatus;
import com.chainsentinel.price.stream.PriceStreamStatusService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@ExtendWith(MockitoExtension.class)
class ChainSentinelRuntimeHealthIndicatorTest {

	@Mock
	private PriceStreamStatusService priceStreamStatusService;

	@Mock
	private OkxBackfillAsyncTaskService okxBackfillAsyncTaskService;

	@Mock
	private DbPriceTickBatchWriter dbPriceTickBatchWriter;

	@Test
	void shouldReportUpWhenProvidersConnectedAndTickIngestHealthy() {
		ChainSentinelRuntimeHealthIndicator indicator = new ChainSentinelRuntimeHealthIndicator(
			priceStreamStatusService,
			okxBackfillAsyncTaskService,
			dbPriceTickBatchWriter
		);
		when(priceStreamStatusService.listStatuses()).thenReturn(List.of(
			providerStatus(true, true, null)
		));
		when(okxBackfillAsyncTaskService.runningTaskCount()).thenReturn(1L);
		when(dbPriceTickBatchWriter.currentStatus()).thenReturn(
			new DbPriceTickBatchWriter.TickIngestStatus(true, 200, 1000, 1000L, 200, 0.0D, 0.2D, 200, false)
		);

		Health health = (Health) indicator.health();

		assertEquals(Status.UP, health.getStatus());
		assertEquals("HEALTHY", health.getDetails().get("tickIngestHealth"));
	}

	@Test
	void shouldReportOutOfServiceWhenTickIngestDegraded() {
		ChainSentinelRuntimeHealthIndicator indicator = new ChainSentinelRuntimeHealthIndicator(
			priceStreamStatusService,
			okxBackfillAsyncTaskService,
			dbPriceTickBatchWriter
		);
		when(priceStreamStatusService.listStatuses()).thenReturn(List.of(
			providerStatus(true, true, null)
		));
		when(okxBackfillAsyncTaskService.runningTaskCount()).thenReturn(0L);
		when(dbPriceTickBatchWriter.currentStatus()).thenReturn(
			new DbPriceTickBatchWriter.TickIngestStatus(true, 200, 1000, 1000L, 200, 0.0D, 0.85D, 850, true)
		);

		Health health = (Health) indicator.health();

		assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
		assertEquals("DEGRADED", health.getDetails().get("tickIngestHealth"));
	}

	@Test
	void shouldReportDownWhenStartedProviderIsDisconnected() {
		ChainSentinelRuntimeHealthIndicator indicator = new ChainSentinelRuntimeHealthIndicator(
			priceStreamStatusService,
			okxBackfillAsyncTaskService,
			dbPriceTickBatchWriter
		);
		when(priceStreamStatusService.listStatuses()).thenReturn(List.of(
			providerStatus(true, false, null)
		));
		when(okxBackfillAsyncTaskService.runningTaskCount()).thenReturn(0L);
		when(dbPriceTickBatchWriter.currentStatus()).thenReturn(
			new DbPriceTickBatchWriter.TickIngestStatus(true, 200, 1000, 1000L, 200, 0.0D, 0.1D, 100, false)
		);

		Health health = (Health) indicator.health();

		assertEquals(Status.DOWN, health.getStatus());
	}

	private PriceStreamProviderStatus providerStatus(boolean started, boolean connected, Instant lastErrorAt) {
		return new PriceStreamProviderStatus(
			"okx_ws",
			started,
			connected,
			false,
			0,
			null,
			Instant.parse("2026-05-03T08:00:00Z"),
			null,
			null,
			lastErrorAt,
			0,
			null,
			0
		);
	}
}
