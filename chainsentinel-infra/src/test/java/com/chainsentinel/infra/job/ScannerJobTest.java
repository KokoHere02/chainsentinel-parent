package com.chainsentinel.infra.job;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.ScannerService;
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

        ScannerJob job = new ScannerJob(scannerService);

        job.run();

        verify(scannerService, times(1)).runOnce();
    }

    @Test
    void shouldSkipWhenPreviousRunIsStillRunning() {
        ScannerJob job = new ScannerJob(scannerService);
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

        ScannerJob job = new ScannerJob(scannerService);

        job.run();
        job.run();

        verify(scannerService, times(2)).runOnce();
    }
}
