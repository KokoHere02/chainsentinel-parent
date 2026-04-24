package com.chainsentinel.infra.job;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.service.AddressHoldingSnapshotService;
import com.chainsentinel.infra.service.AddressHoldingSnapshotService.SnapshotResult;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HoldingSnapshotJobTest {

	@Mock
	private AddressHoldingSnapshotService snapshotService;

	@Test
	void shouldRunSnapshotOnStartup() {
		when(snapshotService.refreshNativeHoldings()).thenReturn(new SnapshotResult(1, 1, 0));
		HoldingSnapshotJob job = new HoldingSnapshotJob(snapshotService);

		job.runOnStartup();

		verify(snapshotService, times(1)).refreshNativeHoldings();
	}

	@Test
	void shouldRunSnapshotOnSchedule() {
		when(snapshotService.refreshNativeHoldings()).thenReturn(new SnapshotResult(2, 1, 0));
		HoldingSnapshotJob job = new HoldingSnapshotJob(snapshotService);

		job.run();

		verify(snapshotService, times(1)).refreshNativeHoldings();
	}

	@Test
	void shouldSkipWhenPreviousRunStillRunning() {
		HoldingSnapshotJob job = new HoldingSnapshotJob(snapshotService);
		AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(job, "running");
		running.set(true);

		job.runOnStartup();

		verify(snapshotService, never()).refreshNativeHoldings();
	}
}

