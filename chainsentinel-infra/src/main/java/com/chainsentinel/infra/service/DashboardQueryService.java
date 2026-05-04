package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.MonitorTreeQueryService;
import com.chainsentinel.core.service.dto.MonitorAddressTreeView;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.entity.PricePullTargetEntity;
import com.chainsentinel.infra.entity.PriceTickEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertEventRepository.AlertRuleCountRow;
import com.chainsentinel.infra.repository.AlertEventRepository.AlertSeverityRow;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import com.chainsentinel.infra.repository.PricePullTargetRepository;
import com.chainsentinel.infra.repository.PriceTickRepository;
import com.chainsentinel.infra.repository.PriceTickRepository.PriceSummaryRow;
import com.chainsentinel.infra.repository.PriceTickRepository.PriceTickAggregateRow;
import com.chainsentinel.price.stream.PriceStreamProviderStatus;
import com.chainsentinel.price.stream.PriceStreamStatusService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DashboardQueryService {

	private static final String DEFAULT_PROVIDER = "okx_ws";

	private final MonitorAddressRepository monitorAddressRepository;
	private final AlertRuleRepository alertRuleRepository;
	private final AlertEventRepository alertEventRepository;
	private final PricePullTargetRepository pricePullTargetRepository;
	private final PriceTickRepository priceTickRepository;
	private final OkxBackfillAsyncTaskService okxBackfillAsyncTaskService;
	private final DbPriceTickBatchWriter dbPriceTickBatchWriter;
	private final PriceStreamStatusService priceStreamStatusService;
	private final SolanaBalanceWsSubscriptionService solanaBalanceWsSubscriptionService;
	private final MonitorTreeQueryService monitorTreeQueryService;

	public DashboardQueryService(
		MonitorAddressRepository monitorAddressRepository,
		AlertRuleRepository alertRuleRepository,
		AlertEventRepository alertEventRepository,
		PricePullTargetRepository pricePullTargetRepository,
		PriceTickRepository priceTickRepository,
		OkxBackfillAsyncTaskService okxBackfillAsyncTaskService,
		DbPriceTickBatchWriter dbPriceTickBatchWriter,
		PriceStreamStatusService priceStreamStatusService,
		SolanaBalanceWsSubscriptionService solanaBalanceWsSubscriptionService,
		MonitorTreeQueryService monitorTreeQueryService
	) {
		this.monitorAddressRepository = monitorAddressRepository;
		this.alertRuleRepository = alertRuleRepository;
		this.alertEventRepository = alertEventRepository;
		this.pricePullTargetRepository = pricePullTargetRepository;
		this.priceTickRepository = priceTickRepository;
		this.okxBackfillAsyncTaskService = okxBackfillAsyncTaskService;
		this.dbPriceTickBatchWriter = dbPriceTickBatchWriter;
		this.priceStreamStatusService = priceStreamStatusService;
		this.solanaBalanceWsSubscriptionService = solanaBalanceWsSubscriptionService;
		this.monitorTreeQueryService = monitorTreeQueryService;
	}

	public OverviewView overview() {
		Instant dayStartUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
		long monitorAddressCount = monitorAddressRepository.count();
		long enabledRuleCount = alertRuleRepository.countEnabled();
		long todayAlertCount = alertEventRepository.countByCreatedAtAfter(dayStartUtc);
		long todayHighSeverityAlertCount = alertEventRepository.countByCreatedAtAfterAndSeverityIn(
			dayStartUtc,
			List.of("HIGH", "CRITICAL")
		);
		long activeTradingPairCount = pricePullTargetRepository.countDistinctInstIdByEnabledTrue();
		long runningBackfillCount = okxBackfillAsyncTaskService.runningTaskCount();
		return new OverviewView(
			monitorAddressCount,
			enabledRuleCount,
			todayAlertCount,
			todayHighSeverityAlertCount,
			activeTradingPairCount,
			runningBackfillCount
		);
	}

	public List<PriceSummaryView> priceSummary(Duration window, int limit) {
		long windowMs = Math.max(1L, window.toMillis());
		int safeLimit = Math.max(1, Math.min(500, limit));
		List<PriceSummaryRow> rows = priceTickRepository.queryLatestPriceSummaries(DEFAULT_PROVIDER, windowMs, safeLimit);
		List<PriceSummaryView> result = new ArrayList<>(rows.size());
		for (PriceSummaryRow row : rows) {
			if (row == null || row.getLatestPrice() == null || row.getLatestQuoteTs() == null) {
				continue;
			}
			BigDecimal baselinePrice = row.getBaselinePrice() == null ? row.getLatestPrice() : row.getBaselinePrice();
			BigDecimal changePct = calculateChangePercent(row.getLatestPrice(), baselinePrice);
			result.add(new PriceSummaryView(
				row.getInstId(),
				row.getLatestPrice(),
				baselinePrice,
				changePct,
				row.getLatestQuoteTs()
			));
		}
		return result;
	}

	public List<PriceTrendPointView> priceTrend(String instId, Long fromTs, Long toTs, long bucketMs, int limit) {
		if (!StringUtils.hasText(instId)) {
			return List.of();
		}
		long safeBucketMs = Math.max(1_000L, bucketMs);
		int safeLimit = Math.max(1, Math.min(20_000, limit));
		List<PriceTickAggregateRow> rows = priceTickRepository.queryTickAggregatesByProviderAndInst(
			DEFAULT_PROVIDER,
			instId.trim().toUpperCase(java.util.Locale.ROOT),
			fromTs,
			toTs,
			safeBucketMs,
			safeLimit
		);
		return rows.stream()
			.filter(row -> row.getBucketStartTs() != null)
			.map(row -> new PriceTrendPointView(
				row.getBucketStartTs(),
				row.getLastPrice(),
				row.getMinPrice(),
				row.getMaxPrice(),
				row.getCount()
			))
			.toList();
	}

	public OkxBackfillAsyncTaskService.TaskSummary backfillSummary(Instant fromAt, Instant toAt) {
		return okxBackfillAsyncTaskService.summarize(fromAt, toAt);
	}

	public List<OkxBackfillAsyncTaskService.TaskStatus> backfillTasks(int page, int size, String status, String instId) {
		return okxBackfillAsyncTaskService.list(page, size, status, instId);
	}

	public AlertSummaryView alertSummary(Instant fromAt, Instant toAt, long bucketSec, int topRules) {
		long safeBucketSec = Math.max(60L, bucketSec);
		int safeTopRules = Math.max(1, Math.min(100, topRules));
		List<AlertTrendPointView> trend = aggregateAlertTrend(fromAt, toAt, safeBucketSec);
		List<AlertSeverityCountView> severities = alertEventRepository.countBySeverityBetween(fromAt, toAt).stream()
			.map(row -> new AlertSeverityCountView(row.getSeverity(), row.getTotal()))
			.toList();
		List<AlertRuleCountRow> ruleRows = alertEventRepository.countByRuleBetween(fromAt, toAt, PageRequest.of(0, safeTopRules));
		Map<Long, String> ruleNameById = alertRuleRepository.findAllById(
			ruleRows.stream().map(AlertRuleCountRow::getRuleId).filter(Objects::nonNull).toList()
		).stream().collect(Collectors.toMap(AlertRuleEntity::getId, AlertRuleEntity::getName));
		List<AlertRuleTopView> topByRule = ruleRows.stream()
			.map(row -> new AlertRuleTopView(row.getRuleId(), ruleNameById.get(row.getRuleId()), row.getTotal()))
			.toList();
		return new AlertSummaryView(trend, severities, topByRule);
	}

	public List<RecentAlertView> recentAlerts(int limit) {
		int safeLimit = Math.max(1, Math.min(500, limit));
		return alertEventRepository.findAllByOrderByIdDesc(PageRequest.of(0, safeLimit)).stream()
			.map(this::toRecentAlertView)
			.toList();
	}

	public DashboardHealthView health() {
		List<PriceStreamProviderStatus> statuses = priceStreamStatusService.listStatuses();
		DbPriceTickBatchWriter.TickIngestStatus tickIngestStatus = dbPriceTickBatchWriter.currentStatus();
		TickIngestHealthView tickIngestHealth = evaluateTickIngestHealth(tickIngestStatus);
		long totalProviders = statuses.size();
		long startedProviders = statuses.stream().filter(PriceStreamProviderStatus::started).count();
		long connectedProviders = statuses.stream().filter(PriceStreamProviderStatus::connected).count();
		Instant recentErrorSince = Instant.now().minus(Duration.ofMinutes(10));
		long providersWithRecentError = statuses.stream()
			.filter(status -> status.lastErrorAt() != null && !status.lastErrorAt().isBefore(recentErrorSince))
			.count();
		return new DashboardHealthView(
			totalProviders,
			startedProviders,
			connectedProviders,
			providersWithRecentError,
			okxBackfillAsyncTaskService.runningTaskCount(),
			tickIngestStatus,
			tickIngestHealth,
			statuses
		);
	}

	private TickIngestHealthView evaluateTickIngestHealth(DbPriceTickBatchWriter.TickIngestStatus status) {
		if (status == null || !status.enabled()) {
			return new TickIngestHealthView("DISABLED", List.of("tick_ingest_disabled"));
		}
		double fillRatio = status.queueFillRatio();
		if (fillRatio >= 1.0D) {
			return new TickIngestHealthView("DEGRADED", List.of("buffer_at_capacity"));
		}
		if (status.flushing() && fillRatio >= 0.8D) {
			return new TickIngestHealthView("DEGRADED", List.of("flush_under_high_load"));
		}
		if (fillRatio >= 0.8D) {
			return new TickIngestHealthView("WARN", List.of("queue_fill_ratio_high"));
		}
		if (status.flushing() && status.queueSize() >= status.highWatermark()) {
			return new TickIngestHealthView("WARN", List.of("active_flush"));
		}
		return new TickIngestHealthView("HEALTHY", List.of("ok"));
	}

	public List<SolanaBalanceWsSubscriptionService.SplRefreshFailureStat> solanaSplFailureTop(
		Instant fromAt,
		Instant toAt,
		int top
	) {
		return solanaBalanceWsSubscriptionService.listSplRefreshFailureTop(fromAt, toAt, top);
	}

	public List<MonitorAddressTreeView> monitoringTree(boolean enabledOnly, int limit) {
		int safeLimit = Math.max(1, Math.min(500, limit));
		return monitorTreeQueryService.tree(enabledOnly, safeLimit);
	}

	private BigDecimal calculateChangePercent(BigDecimal latest, BigDecimal baseline) {
		if (latest == null || baseline == null || baseline.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.ZERO;
		}
		return latest.subtract(baseline)
			.multiply(new BigDecimal("100"))
			.divide(baseline, 6, RoundingMode.HALF_UP);
	}

	private RecentAlertView toRecentAlertView(AlertEventEntity alert) {
		return new RecentAlertView(
			alert.getId(),
			alert.getRuleId(),
			alert.getSeverity(),
			alert.getSendStatus(),
			alert.getLastError(),
			alert.getCreatedAt(),
			alert.getSentAt()
		);
	}

	private List<AlertTrendPointView> aggregateAlertTrend(Instant fromAt, Instant toAt, long bucketSec) {
		if (fromAt == null || toAt == null || fromAt.isAfter(toAt)) {
			return List.of();
		}
		return alertEventRepository.countTrendByBucketBetween(fromAt, toAt, bucketSec).stream()
			.map(row -> new AlertTrendPointView(row.getBucketStartTs(), row.getTotal()))
			.toList();
	}

	public record OverviewView(
		long monitorAddressCount,
		long enabledRuleCount,
		long todayAlertCount,
		long todayHighSeverityAlertCount,
		long activeTradingPairCount,
		long runningBackfillCount
	) {
	}

	public record PriceSummaryView(
		String instId,
		BigDecimal latestPrice,
		BigDecimal baselinePrice,
		BigDecimal changePct,
		Long latestQuoteTs
	) {
	}

	public record PriceTrendPointView(
		Long bucketStartTs,
		BigDecimal lastPrice,
		BigDecimal minPrice,
		BigDecimal maxPrice,
		Long count
	) {
	}

	public record AlertTrendPointView(Long bucketStartTs, Long total) {
	}

	public record AlertSeverityCountView(String severity, Long total) {
	}

	public record AlertRuleTopView(Long ruleId, String ruleName, Long total) {
	}

	public record AlertSummaryView(
		List<AlertTrendPointView> trend,
		List<AlertSeverityCountView> severities,
		List<AlertRuleTopView> topByRule
	) {
	}

	public record RecentAlertView(
		Long id,
		Long ruleId,
		String severity,
		String sendStatus,
		String lastError,
		Instant createdAt,
		Instant sentAt
	) {
	}

	public record DashboardHealthView(
		long totalProviders,
		long startedProviders,
		long connectedProviders,
		long providersWithRecentError,
		long runningBackfillCount,
		DbPriceTickBatchWriter.TickIngestStatus tickIngest,
		TickIngestHealthView tickIngestHealth,
		List<PriceStreamProviderStatus> wsProviders
	) {
	}

	public record TickIngestHealthView(
		String status,
		List<String> reasons
	) {
	}
}
