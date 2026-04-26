package com.chainsentinel.web.api;

import com.chainsentinel.core.service.dto.MonitorAddressTreeView;
import com.chainsentinel.infra.service.DashboardQueryService;
import com.chainsentinel.infra.service.OkxBackfillAsyncTaskService;
import com.chainsentinel.infra.service.SolanaBalanceWsSubscriptionService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/dashboard")
@Validated
public class DashboardController {

	private final DashboardQueryService dashboardQueryService;

	public DashboardController(DashboardQueryService dashboardQueryService) {
		this.dashboardQueryService = dashboardQueryService;
	}

	@GetMapping("/overview")
	public DashboardQueryService.OverviewView overview() {
		return dashboardQueryService.overview();
	}

	@GetMapping("/price/summary")
	public List<DashboardQueryService.PriceSummaryView> priceSummary(
		@RequestParam(name = "window", defaultValue = "24h") String window,
		@RequestParam(name = "limit", defaultValue = "50") int limit
	) {
		if (limit < 1 || limit > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 500");
		}
		return dashboardQueryService.priceSummary(parseWindow(window), limit);
	}

	@GetMapping("/price/trend")
	public List<DashboardQueryService.PriceTrendPointView> priceTrend(
		@RequestParam(name = "instId") String instId,
		@RequestParam(name = "from", required = false) Long from,
		@RequestParam(name = "to", required = false) Long to,
		@RequestParam(name = "bucketMs", defaultValue = "60000") long bucketMs,
		@RequestParam(name = "limit", defaultValue = "5000") int limit
	) {
		if (bucketMs < 1_000L || bucketMs > 86_400_000L) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bucketMs must be between 1000 and 86400000");
		}
		if (limit < 1 || limit > 20_000) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 20000");
		}
		return dashboardQueryService.priceTrend(instId, from, to, bucketMs, limit);
	}

	@GetMapping("/backfill/summary")
	public OkxBackfillAsyncTaskService.TaskSummary backfillSummary(
		@RequestParam(name = "from", required = false) Long from,
		@RequestParam(name = "to", required = false) Long to
	) {
		return dashboardQueryService.backfillSummary(toInstant(from), toInstant(to));
	}

	@GetMapping("/backfill/tasks")
	public List<OkxBackfillAsyncTaskService.TaskStatus> backfillTasks(
		@RequestParam(name = "page", defaultValue = "0") int page,
		@RequestParam(name = "size", defaultValue = "20") int size,
		@RequestParam(name = "status", required = false) String status,
		@RequestParam(name = "instId", required = false) String instId
	) {
		return dashboardQueryService.backfillTasks(page, size, status, instId);
	}

	@GetMapping("/alerts/summary")
	public DashboardQueryService.AlertSummaryView alertSummary(
		@RequestParam(name = "from", required = false) Long from,
		@RequestParam(name = "to", required = false) Long to,
		@RequestParam(name = "bucketSec", defaultValue = "3600") long bucketSec,
		@RequestParam(name = "topRules", defaultValue = "10") int topRules
	) {
		if (bucketSec < 60L || bucketSec > 86_400L) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bucketSec must be between 60 and 86400");
		}
		if (topRules < 1 || topRules > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topRules must be between 1 and 100");
		}
		Instant fromAt = toInstantOrDefault(from, Instant.now().minus(Duration.ofDays(1)));
		Instant toAt = toInstantOrDefault(to, Instant.now());
		return dashboardQueryService.alertSummary(fromAt, toAt, bucketSec, topRules);
	}

	@GetMapping("/alerts/recent")
	public List<DashboardQueryService.RecentAlertView> recentAlerts(
		@RequestParam(name = "limit", defaultValue = "50") int limit
	) {
		if (limit < 1 || limit > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 500");
		}
		return dashboardQueryService.recentAlerts(limit);
	}

	@GetMapping("/health")
	public DashboardQueryService.DashboardHealthView health() {
		return dashboardQueryService.health();
	}

	@GetMapping("/solana/spl-failures")
	public List<SolanaBalanceWsSubscriptionService.SplRefreshFailureStat> solanaSplFailures(
		@RequestParam(name = "from", required = false) Long from,
		@RequestParam(name = "to", required = false) Long to,
		@RequestParam(name = "top", defaultValue = "20") int top
	) {
		if (top < 1 || top > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "top must be between 1 and 100");
		}
		return dashboardQueryService.solanaSplFailureTop(toInstant(from), toInstant(to), top);
	}

	@GetMapping("/monitoring/tree")
	public List<MonitorAddressTreeView> monitoringTree(
		@RequestParam(name = "enabledOnly", defaultValue = "true") boolean enabledOnly,
		@RequestParam(name = "limit", defaultValue = "200") int limit
	) {
		if (limit < 1 || limit > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 500");
		}
		return dashboardQueryService.monitoringTree(enabledOnly, limit);
	}

	private Instant toInstant(Long value) {
		if (value == null || value <= 0) {
			return null;
		}
		return Instant.ofEpochMilli(value);
	}

	private Instant toInstantOrDefault(Long value, Instant defaultValue) {
		Instant converted = toInstant(value);
		return converted == null ? defaultValue : converted;
	}

	private Duration parseWindow(String window) {
		if (window == null || window.isBlank()) {
			return Duration.ofHours(24);
		}
		String normalized = window.trim().toLowerCase();
		try {
			if (normalized.endsWith("ms")) {
				return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
			}
			if (normalized.endsWith("m")) {
				return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
			}
			if (normalized.endsWith("h")) {
				return Duration.ofHours(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
			}
			if (normalized.endsWith("d")) {
				return Duration.ofDays(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
			}
		} catch (Exception ignored) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid window format, examples: 15m, 24h, 7d");
		}
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid window format, examples: 15m, 24h, 7d");
	}
}
