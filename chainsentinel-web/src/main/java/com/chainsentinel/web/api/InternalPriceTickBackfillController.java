package com.chainsentinel.web.api;

import com.chainsentinel.infra.service.OkxBackfillAsyncTaskService;
import com.chainsentinel.web.api.support.ratelimit.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/price-ticks/backfill")
public class InternalPriceTickBackfillController {

	private final OkxBackfillAsyncTaskService okxBackfillAsyncTaskService;

	public InternalPriceTickBackfillController(OkxBackfillAsyncTaskService okxBackfillAsyncTaskService) {
		this.okxBackfillAsyncTaskService = okxBackfillAsyncTaskService;
	}

	@PostMapping("/okx")
	@RateLimit(
		name = "internal.price-ticks.backfill.okx",
		permits = 2,
		windowSeconds = 10,
		scope = RateLimit.Scope.IP,
		message = "Backfill too frequent, retry later"
	)
	public OkxBackfillAsyncTaskService.TaskAccepted backfillOkx(@RequestBody @Valid BackfillRequest request) {
		if (request.fromTs() > request.toTs()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromTs must be less than or equal to toTs");
		}
		return okxBackfillAsyncTaskService.submit(
			request.instId(),
			request.fromTs(),
			request.toTs(),
			request.bar(),
			request.pageLimit(),
			request.maxRounds(),
			request.sleepMs()
		);
	}

	@GetMapping("/okx/tasks/{taskId}")
	public OkxBackfillAsyncTaskService.TaskStatus queryTask(@PathVariable String taskId) {
		OkxBackfillAsyncTaskService.TaskStatus status = okxBackfillAsyncTaskService.query(taskId);
		if (status == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found: " + taskId);
		}
		return status;
	}

	public record BackfillRequest(
		@NotBlank String instId,
		@Min(1) long fromTs,
		@Min(1) long toTs,
		String bar,
		@Min(1) @Max(300) Integer pageLimit,
		@Min(1) @Max(1000) Integer maxRounds,
		@Min(0) @Max(1000) Long sleepMs
	) {
		public BackfillRequest {
			if (bar == null || bar.isBlank()) {
				bar = "1m";
			}
			if (pageLimit == null) {
				pageLimit = 300;
			}
			if (maxRounds == null) {
				maxRounds = 200;
			}
			if (sleepMs == null) {
				sleepMs = 120L;
			}
		}
	}
}
