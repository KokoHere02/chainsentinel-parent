package com.chainsentinel.web.api.support;

import com.chainsentinel.infra.service.OkxBackfillAsyncTaskService;
import com.chainsentinel.infra.service.DbPriceTickBatchWriter;
import com.chainsentinel.price.stream.PriceStreamProviderStatus;
import com.chainsentinel.price.stream.PriceStreamStatusService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component("chainsentinelRuntime")
public class ChainSentinelRuntimeHealthIndicator implements HealthIndicator {

	private static final Duration RECENT_ERROR_WINDOW = Duration.ofMinutes(10);

	private final PriceStreamStatusService priceStreamStatusService;
	private final OkxBackfillAsyncTaskService okxBackfillAsyncTaskService;
	private final DbPriceTickBatchWriter dbPriceTickBatchWriter;

	public ChainSentinelRuntimeHealthIndicator(
		PriceStreamStatusService priceStreamStatusService,
		OkxBackfillAsyncTaskService okxBackfillAsyncTaskService,
		DbPriceTickBatchWriter dbPriceTickBatchWriter
	) {
		this.priceStreamStatusService = priceStreamStatusService;
		this.okxBackfillAsyncTaskService = okxBackfillAsyncTaskService;
		this.dbPriceTickBatchWriter = dbPriceTickBatchWriter;
	}

	@Override
	public Health health() {
		List<PriceStreamProviderStatus> providers = priceStreamStatusService.listStatuses();
		long startedProviders = providers.stream().filter(PriceStreamProviderStatus::started).count();
		long connectedProviders = providers.stream().filter(PriceStreamProviderStatus::connected).count();
		Instant recentErrorSince = Instant.now().minus(RECENT_ERROR_WINDOW);
		long recentErrorProviders = providers.stream()
			.filter(status -> status.lastErrorAt() != null && !status.lastErrorAt().isBefore(recentErrorSince))
			.count();
		DbPriceTickBatchWriter.TickIngestStatus tickIngestStatus = dbPriceTickBatchWriter.currentStatus();
		String tickIngestHealth = resolveTickIngestHealth(tickIngestStatus);

		Status overallStatus = resolveStatus(startedProviders, connectedProviders, recentErrorProviders, tickIngestHealth);
		Health.Builder builder = Status.DOWN.equals(overallStatus) || Status.OUT_OF_SERVICE.equals(overallStatus)
			? Health.status(overallStatus)
			: Health.up();

		Map<String, Object> wsDetails = new LinkedHashMap<>();
		wsDetails.put("totalProviders", providers.size());
		wsDetails.put("startedProviders", startedProviders);
		wsDetails.put("connectedProviders", connectedProviders);
		wsDetails.put("providersWithRecentError", recentErrorProviders);
		wsDetails.put("providers", providers);

		Map<String, Object> backfillDetails = new LinkedHashMap<>();
		backfillDetails.put("runningTaskCount", okxBackfillAsyncTaskService.runningTaskCount());

		return builder
			.withDetail("priceStream", wsDetails)
			.withDetail("backfill", backfillDetails)
			.withDetail("tickIngest", tickIngestStatus)
			.withDetail("tickIngestHealth", tickIngestHealth)
			.build();
	}

	private Status resolveStatus(long startedProviders, long connectedProviders, long recentErrorProviders, String tickIngestHealth) {
		if (startedProviders > 0 && connectedProviders == 0) {
			return Status.DOWN;
		}
		if (startedProviders > 0 && connectedProviders < startedProviders) {
			return Status.OUT_OF_SERVICE;
		}
		if (recentErrorProviders > 0) {
			return Status.OUT_OF_SERVICE;
		}
		if ("DEGRADED".equals(tickIngestHealth)) {
			return Status.OUT_OF_SERVICE;
		}
		return Status.UP;
	}

	private String resolveTickIngestHealth(DbPriceTickBatchWriter.TickIngestStatus status) {
		if (status == null || !status.enabled()) {
			return "DISABLED";
		}
		if (status.queueFillRatio() >= 1.0D) {
			return "DEGRADED";
		}
		if (status.flushing() && status.queueFillRatio() >= 0.8D) {
			return "DEGRADED";
		}
		if (status.queueFillRatio() >= 0.8D) {
			return "WARN";
		}
		return "HEALTHY";
	}
}
