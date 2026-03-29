package com.chainsentinel.web.api;

import com.chainsentinel.core.service.ScannerService;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scanner")
@Validated
public class ScannerController {

    private final ScannerService scannerService;

    public ScannerController(ScannerService scannerService) {
        this.scannerService = scannerService;
    }

    @PostMapping("/run")
    public ScanRunResponse run(
            @RequestParam(name = "times", defaultValue = "1") @Min(1) Integer times,
            @RequestParam(name = "full", defaultValue = "false") Boolean full
    ) {
        int inserted = 0;
        for (int i = 0; i < times; i++) {
            inserted += scannerService.runOnce(Boolean.TRUE.equals(full));
        }
        return new ScanRunResponse(inserted, Instant.now());
    }

    public record ScanRunResponse(int insertedCount, Instant executedAt) {
    }
}
