package com.chainsentinel.web.api;

import com.chainsentinel.core.service.ScannerService;
import com.chainsentinel.web.api.support.ratelimit.RateLimit;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scanner")
@Validated
@Profile("dev")
public class ScannerController {

	private final ScannerService scannerService;

	public ScannerController(ScannerService scannerService) {
		this.scannerService = scannerService;
	}

	@PostMapping("/run")
	@RateLimit(
		name = "scanner.run",
		permits = 2,
		windowSeconds = 10,
		scope = RateLimit.Scope.IP,
		message = "Scanner run too frequent, retry later"
	)
	public ScanRunResponse run(
		@RequestParam(name = "times", defaultValue = "1") @Min(1) Integer times
	) {
		int inserted = 0;
		for (int i = 0; i < times; i++) {
			inserted += scannerService.runOnce();
		}
		return new ScanRunResponse(inserted, Instant.now());
	}

	public record ScanRunResponse(int insertedCount, Instant executedAt) {
	}
}
