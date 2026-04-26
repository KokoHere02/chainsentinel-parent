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
import com.chainsentinel.infra.repository.AlertEventRepository.AlertTrendRow;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import com.chainsentinel.infra.repository.PricePullTargetRepository;
import com.chainsentinel.infra.repository.PriceTickRepository;
import com.chainsentinel.price.stream.PriceStreamProviderStatus;
import com.chainsentinel.price.stream.PriceStreamStatusService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
		this.priceStreamStatusService = priceStreamStatusService;
		this.solanaBalanceWsSubscriptionService = solanaBalanceWsSubscriptionService;
		this.monitorTreeQueryService = monitorTreeQueryService;
	}

	public OverviewView overview() {
		Instant dayStartUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
		long monitorAddressCount = monitorAddressRepository.count();
		long enabledRuleCount = alertRuleRepository.findByEnabledTrue().size();
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
		Set<String> uniqueInstIds = new LinkedHashSet<>();
		for (PricePullTargetEntity target : pricePullTargetRepository.findByEnabledTrueOrderByPriorityAscIdAsc()) {
			if (target == null || !StringUtils.hasText(target.getInstId())) {
				continue;
			}
			uniqueInstIds.add(target.getInstId().trim().toUpperCase(Locale.ROOT));
			if (uniqueInstIds.size() >= safeLimit) {
				break;
			}
		}

		List<PriceSummaryView> result = new ArrayList<>(uniqueInstIds.size());
		for (String instId : uniqueInstIds) {
			PriceTickEntity latest = priceTickRepository
				.findTopByProviderNameAndInstIdOrderByQuoteTsDesc(DEFAULT_PROVIDER, instId)
				.orElse(null);
			if (latest == null || latest.getPrice() == null || latest.getQuoteTs() == null) {
				continue;
			}
			long fromTs = Math.max(1L, latest.getQuoteTs() - windowMs);
			PriceTickEntity baseline = priceTickRepository
				.queryEarliestTickSince(DEFAULT_PROVIDER, instId, fromTs)
				.orElse(latest);
			BigDecimal baselinePrice = baseline.getPrice() == null ? latest.getPrice() : baseline.getPrice();
			BigDecimal changePct = calculateChangePercent(latest.getPrice(), baselinePrice);
			result.add(new PriceSummaryView(
				instId,
				latest.getPrice(),
				baselinePrice,
				changePct,
				latest.getQuoteTs()
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
		String normalizedInstId = instId.trim().toUpperCase(Locale.ROOT);
		List<PriceTickRepository.PriceTickAggregateRow> rows = priceTickRepository.queryTickAggregatesByProviderAndInst(
			DEFAULT_PROVIDER,
			normalizedInstId,
			fromTs,
			toTs,
			safeBucketMs,
			safeLimit
		);
		List<PriceTrendPointView> points = new ArrayList<>(rows.size());
		for (int i = rows.size() - 1; i >= 0; i--) {
			PriceTickRepository.PriceTickAggregateRow row = rows.get(i);
			points.add(new PriceTrendPointView(
				row.getBucketStartTs(),
				row.getLastPrice(),
				row.getMinPrice(),
				row.getMaxPrice(),
				row.getCount()
			));
		}
		return points;
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
		List<AlertTrendPointView> trend = alertEventRepository.countTrendByBucket(fromAt, toAt, safeBucketSec).stream()
			.map(row -> new AlertTrendPointView(row.getBucketStartSec() * 1000L, row.getTotal()))
			.toList();
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
			statuses
		);
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
		List<PriceStreamProviderStatus> wsProviders
	) {
	}
}
