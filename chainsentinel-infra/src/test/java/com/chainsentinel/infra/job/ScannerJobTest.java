package com.chainsentinel.infra.job;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.ScannerService;
import com.chainsentinel.infra.config.ScannerProperties;
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
    void shouldRunScannerWithConfiguredFullFlag() {
        ScannerProperties properties = new ScannerProperties();
        properties.setFullEthScan(true);
        when(scannerService.runOnce(true)).thenReturn(3);

        ScannerJob job = new ScannerJob(scannerService, properties);

        job.run();

        verify(scannerService, times(1)).runOnce(true);
    }

    @Test
    void shouldSkipWhenPreviousRunIsStillRunning() {
        ScannerProperties properties = new ScannerProperties();
        ScannerJob job = new ScannerJob(scannerService, properties);
        AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(job, "running");
        running.set(true);

        job.run();

        verify(scannerService, never()).runOnce(false);
    }

    @Test
    void shouldContinueAfterFailure() {
        ScannerProperties properties = new ScannerProperties();
        when(scannerService.runOnce(false))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(1);

        ScannerJob job = new ScannerJob(scannerService, properties);

        job.run();
        job.run();

        verify(scannerService, times(2)).runOnce(false);
    }
}
