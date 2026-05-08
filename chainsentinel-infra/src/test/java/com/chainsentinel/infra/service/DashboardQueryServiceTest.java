package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.MonitorTreeQueryService;
import com.chainsentinel.infra.entity.AssetPriceSnapshotEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.repository.AssetPriceSnapshotRepository;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import com.chainsentinel.infra.repository.PricePullTargetRepository;
import com.chainsentinel.infra.repository.PriceTickRepository;
import com.chainsentinel.price.stream.PriceStreamProviderStatus;
import com.chainsentinel.price.stream.PriceStreamStatusService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardQueryServiceTest {

	@Mock
	private MonitorAddressRepository monitorAddressRepository;
	@Mock
	private AlertRuleRepository alertRuleRepository;
	@Mock
	private AlertEventRepository alertEventRepository;
	@Mock
	private PricePullTargetRepository pricePullTargetRepository;
	@Mock
	private AssetPriceSnapshotRepository assetPriceSnapshotRepository;
	@Mock
	private PriceTickRepository priceTickRepository;
	@Mock
	private OkxBackfillAsyncTaskService okxBackfillAsyncTaskService;
	@Mock
	private DbPriceTickBatchWriter dbPriceTickBatchWriter;
	@Mock
	private PriceStreamStatusService priceStreamStatusService;
	@Mock
	private SolanaBalanceWsSubscriptionService solanaBalanceWsSubscriptionService;
	@Mock
	private MonitorTreeQueryService monitorTreeQueryService;

	@Test
	void priceSummaryShouldUseSnapshotQueries() {
		DashboardQueryService service = new DashboardQueryService(
			monitorAddressRepository,
			alertRuleRepository,
			alertEventRepository,
			pricePullTargetRepository,
			assetPriceSnapshotRepository,
			priceTickRepository,
			okxBackfillAsyncTaskService,
			dbPriceTickBatchWriter,
			priceStreamStatusService,
			solanaBalanceWsSubscriptionService,
			monitorTreeQueryService
		);

		when(pricePullTargetRepository.findDistinctEnabledInstIds(eq(2)))
			.thenReturn(List.of("BTC-USDT", "ETH-USDT"));

		AssetPriceSnapshotEntity btcLatest = snapshot("okx", "BTC-USDT", "110", "2026-05-08T12:05:00");
		AssetPriceSnapshotEntity ethLatest = snapshot("okx", "ETH-USDT", "200", "2026-05-08T12:10:00");
		when(assetPriceSnapshotRepository.findLatestByInstIdIn(eq(List.of("BTC-USDT", "ETH-USDT"))))
			.thenReturn(List.of(btcLatest, ethLatest));
		when(assetPriceSnapshotRepository.findTopByProviderNameAndInstIdAndBucketTsGreaterThanEqualOrderByBucketTsAsc(
			eq("okx"),
			eq("BTC-USDT"),
			eq(LocalDateTime.of(2026, 5, 8, 11, 5))
		)).thenReturn(java.util.Optional.of(snapshot("okx", "BTC-USDT", "100", "2026-05-08T12:00:00")));
		when(assetPriceSnapshotRepository.findTopByProviderNameAndInstIdAndBucketTsGreaterThanEqualOrderByBucketTsAsc(
			eq("okx"),
			eq("ETH-USDT"),
			eq(LocalDateTime.of(2026, 5, 8, 11, 10))
		)).thenReturn(java.util.Optional.of(snapshot("okx", "ETH-USDT", "190", "2026-05-08T12:00:00")));

		List<DashboardQueryService.PriceSummaryView> result = service.priceSummary(Duration.ofHours(1), 2);

		assertEquals(2, result.size());
		assertEquals("BTC-USDT", result.get(0).instId());
		assertEquals(new BigDecimal("110"), result.get(0).latestPrice());
		assertEquals(new BigDecimal("100"), result.get(0).baselinePrice());
		assertTrue(result.get(0).changePct().compareTo(new BigDecimal("10.000000")) == 0);
		assertEquals("ETH-USDT", result.get(1).instId());
		assertEquals(new BigDecimal("200"), result.get(1).latestPrice());
		assertEquals(new BigDecimal("190"), result.get(1).baselinePrice());
		assertTrue(result.get(1).changePct().compareTo(new BigDecimal("5.263158")) == 0);
	}

	private AssetPriceSnapshotEntity snapshot(String providerName, String instId, String price, String quotedAt) {
		AssetPriceSnapshotEntity entity = new AssetPriceSnapshotEntity();
		entity.setInstId(instId);
		entity.setProviderName(providerName);
		entity.setPrice(new BigDecimal(price));
		entity.setBucketTs(LocalDateTime.parse(quotedAt));
		entity.setQuotedAt(LocalDateTime.parse(quotedAt));
		return entity;
	}
}
