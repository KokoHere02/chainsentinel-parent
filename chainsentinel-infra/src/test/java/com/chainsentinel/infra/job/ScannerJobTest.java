package com.chainsentinel.infra.job;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.config.ScannerProperties;
import com.chainsentinel.core.service.ScannerService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScannerJobTest {

    @Mock
    private ScannerService scannerService;

    @Test
    void shouldRunScanner() {
        when(scannerService.runOnce()).thenReturn(3);

        ScannerJob job = new ScannerJob(scannerService, new ScannerProperties(), new SimpleMeterRegistry());

        job.run();

        verify(scannerService, times(1)).runOnce();
    }

    @Test
    void shouldSkipWhenPreviousRunIsStillRunning() {
        ScannerJob job = new ScannerJob(scannerService, new ScannerProperties(), new SimpleMeterRegistry());
        AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(job, "running");
        running.set(true);

        job.run();

        verify(scannerService, never()).runOnce();
    }

    @Test
    void shouldContinueAfterFailure() {
        when(scannerService.runOnce())
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(1);

        ScannerJob job = new ScannerJob(scannerService, new ScannerProperties(), new SimpleMeterRegistry());

        job.run();
        job.run();

        verify(scannerService, times(2)).runOnce();
    }

	@Test
	void shouldSkipStartupRunWhenDisabledByConfig() {
		ScannerProperties properties = new ScannerProperties();
		properties.setStartupRunOnReady(false);
		ScannerJob job = new ScannerJob(scannerService, properties, new SimpleMeterRegistry());

		job.runOnStartup();

		verify(scannerService, never()).runOnce();
	}
}
