package com.chainsentinel.infra.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.PriceSnapshotService;
import com.chainsentinel.core.service.dto.PriceSnapshotView;
import com.chainsentinel.infra.config.PriceIngestProperties;
import com.chainsentinel.infra.entity.AssetPriceSnapshotEntity;
import com.chainsentinel.infra.entity.PriceProviderConfigEntity;
import com.chainsentinel.infra.entity.PricePullTargetEntity;
import com.chainsentinel.infra.repository.AssetPriceSnapshotRepository;
import com.chainsentinel.infra.repository.PriceProviderConfigRepository;
import com.chainsentinel.infra.repository.PricePullTargetRepository;
import com.chainsentinel.price.api.PriceService;
import com.chainsentinel.price.api.dto.PriceQuote;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PriceIngestJobTest {

  @Mock
  private PriceService priceService;

  @Mock
  private PriceSnapshotService priceSnapshotService;

  @Mock
  private PricePullTargetRepository pricePullTargetRepository;

  @Mock
  private PriceProviderConfigRepository priceProviderConfigRepository;

  @Mock
  private AssetPriceSnapshotRepository assetPriceSnapshotRepository;

  @Test
  void shouldIngestEnabledPullTarget() {
    PriceIngestProperties properties = new PriceIngestProperties();
    properties.setEnabled(true);

    PricePullTargetEntity target = new PricePullTargetEntity();
    ReflectionTestUtils.setField(target, "id", 100L);
    target.setAssetId(1L);
    target.setProviderConfigId(10L);
    target.setInstType("SPOT");
    target.setInstId("BTC-USDT");
    target.setQuoteSymbol("USDT");
    target.setEnabled(true);
    target.setPriority(1);

    PriceProviderConfigEntity provider = new PriceProviderConfigEntity();
    ReflectionTestUtils.setField(provider, "id", 10L);
    provider.setProviderName("okx");
    provider.setEnabled(true);

    when(pricePullTargetRepository.findByEnabledTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(target));
    when(priceProviderConfigRepository.findByIdAndEnabledTrue(10L)).thenReturn(Optional.of(provider));
    when(priceService.getQuote(any())).thenReturn(Optional.of(new PriceQuote(
      "BTC",
      "USDT",
      new BigDecimal("100000.12"),
      Instant.parse("2026-04-03T12:34:56Z").toEpochMilli(),
      "okx",
      false
    )));
    when(priceSnapshotService.upsertMinuteSnapshot(any())).thenReturn(new PriceSnapshotView(
      1L,
      1L,
      "okx",
      "SPOT",
      "BTC-USDT",
      "USDT",
      new BigDecimal("100000.12"),
      LocalDateTime.of(2026, 4, 3, 12, 34),
      LocalDateTime.of(2026, 4, 3, 12, 34, 56),
      Instant.now()
    ));

    PriceIngestJob job = new PriceIngestJob(
      priceService,
      priceSnapshotService,
      pricePullTargetRepository,
      priceProviderConfigRepository,
      assetPriceSnapshotRepository,
      properties,
      new SimpleMeterRegistry()
    );
    job.run();

    verify(priceService, times(1)).getQuote(any());
    verify(priceSnapshotService, times(1)).upsertMinuteSnapshot(any());
  }

  @Test
  void shouldSkipByPollInterval() {
    PriceIngestProperties properties = new PriceIngestProperties();
    properties.setEnabled(true);

    PricePullTargetEntity target = new PricePullTargetEntity();
    ReflectionTestUtils.setField(target, "id", 101L);
    target.setAssetId(1L);
    target.setProviderConfigId(10L);
    target.setInstType("SPOT");
    target.setInstId("BTC-USDT");
    target.setQuoteSymbol("USDT");
    target.setPollIntervalMs(60000);
    target.setEnabled(true);
    target.setPriority(1);

    PriceProviderConfigEntity provider = new PriceProviderConfigEntity();
    ReflectionTestUtils.setField(provider, "id", 10L);
    provider.setProviderName("okx");
    provider.setEnabled(true);

    AssetPriceSnapshotEntity snapshot = new AssetPriceSnapshotEntity();
    snapshot.setBucketTs(LocalDateTime.now().minusSeconds(10));

    when(pricePullTargetRepository.findByEnabledTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(target));
    when(priceProviderConfigRepository.findByIdAndEnabledTrue(10L)).thenReturn(Optional.of(provider));
    when(assetPriceSnapshotRepository.findTopByAssetIdAndProviderNameAndInstIdOrderByBucketTsDesc(1L, "okx", "BTC-USDT"))
      .thenReturn(Optional.of(snapshot));

    PriceIngestJob job = new PriceIngestJob(
      priceService,
      priceSnapshotService,
      pricePullTargetRepository,
      priceProviderConfigRepository,
      assetPriceSnapshotRepository,
      properties,
      new SimpleMeterRegistry()
    );
    job.run();

    verify(priceService, never()).getQuote(any());
    verify(priceSnapshotService, never()).upsertMinuteSnapshot(any());
  }

  @Test
  void shouldSkipWhenDisabled() {
    PriceIngestProperties properties = new PriceIngestProperties();
    properties.setEnabled(false);

    PriceIngestJob job = new PriceIngestJob(
      priceService,
      priceSnapshotService,
      pricePullTargetRepository,
      priceProviderConfigRepository,
      assetPriceSnapshotRepository,
      properties,
      new SimpleMeterRegistry()
    );
    job.run();

    verify(pricePullTargetRepository, never()).findByEnabledTrueOrderByPriorityAscIdAsc();
  }

  @Test
  void shouldSkipWhenPreviousRunStillRunning() {
    PriceIngestProperties properties = new PriceIngestProperties();
    properties.setEnabled(true);

    PriceIngestJob job = new PriceIngestJob(
      priceService,
      priceSnapshotService,
      pricePullTargetRepository,
      priceProviderConfigRepository,
      assetPriceSnapshotRepository,
      properties,
      new SimpleMeterRegistry()
    );
    AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(job, "running");
    running.set(true);

    job.run();

    verify(pricePullTargetRepository, never()).findByEnabledTrueOrderByPriorityAscIdAsc();
  }

  @Test
  void shouldSkipStartupRunWhenDisabledByConfig() {
    PriceIngestProperties properties = new PriceIngestProperties();
    properties.setEnabled(true);
    properties.setStartupRunOnReady(false);

    PriceIngestJob job = new PriceIngestJob(
      priceService,
      priceSnapshotService,
      pricePullTargetRepository,
      priceProviderConfigRepository,
      assetPriceSnapshotRepository,
      properties,
      new SimpleMeterRegistry()
    );

    job.runOnStartup();

    verify(pricePullTargetRepository, never()).findByEnabledTrueOrderByPriorityAscIdAsc();
  }
}
