package com.chainsentinel.infra.job;

import com.chainsentinel.core.service.PriceSnapshotService;
import com.chainsentinel.core.service.dto.PriceSnapshotUpsertCommand;
import com.chainsentinel.infra.config.PriceIngestProperties;
import com.chainsentinel.infra.entity.PriceProviderConfigEntity;
import com.chainsentinel.infra.entity.PricePullTargetEntity;
import com.chainsentinel.infra.repository.AssetPriceSnapshotRepository;
import com.chainsentinel.infra.repository.PriceProviderConfigRepository;
import com.chainsentinel.infra.repository.PricePullTargetRepository;
import com.chainsentinel.price.api.PriceService;
import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PriceIngestJob {

  private static final Logger log = LoggerFactory.getLogger(PriceIngestJob.class);

  private final PriceService priceService;
  private final PriceSnapshotService priceSnapshotService;
  private final PricePullTargetRepository pricePullTargetRepository;
  private final PriceProviderConfigRepository priceProviderConfigRepository;
  private final AssetPriceSnapshotRepository assetPriceSnapshotRepository;
  private final PriceIngestProperties priceIngestProperties;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public PriceIngestJob(
    PriceService priceService,
    PriceSnapshotService priceSnapshotService,
    PricePullTargetRepository pricePullTargetRepository,
    PriceProviderConfigRepository priceProviderConfigRepository,
    AssetPriceSnapshotRepository assetPriceSnapshotRepository,
    PriceIngestProperties priceIngestProperties
  ) {
    this.priceService = priceService;
    this.priceSnapshotService = priceSnapshotService;
    this.pricePullTargetRepository = pricePullTargetRepository;
    this.priceProviderConfigRepository = priceProviderConfigRepository;
    this.assetPriceSnapshotRepository = assetPriceSnapshotRepository;
    this.priceIngestProperties = priceIngestProperties;
  }

  @Scheduled(fixedDelayString = "${chainsentinel.price.ingest.interval-ms:15000}")
  public void run() {
    if (!priceIngestProperties.isEnabled()) {
      return;
    }
    if (!running.compareAndSet(false, true)) {
      log.warn("price.ingest.skip previous run still in progress");
      return;
    }

    int success = 0;
    int total = 0;
    try {
      List<PricePullTargetEntity> targets = pricePullTargetRepository.findByEnabledTrueOrderByPriorityAscIdAsc();
      for (PricePullTargetEntity target : targets) {
        total++;
        if (ingestOne(target)) {
          success++;
        }
      }
      log.info("price.ingest.job.done success={} total={}", success, total);
    } catch (Exception ex) {
      log.error("price.ingest.job.failed", ex);
    } finally {
      running.set(false);
    }
  }

  private boolean ingestOne(PricePullTargetEntity target) {
    if (target == null || target.getAssetId() == null || !StringUtils.hasText(target.getInstId()) || !StringUtils.hasText(target.getInstType()) || !StringUtils.hasText(target.getQuoteSymbol())) {
      log.warn("price.ingest.invalid_target targetId={}", target == null ? null : target.getId());
      return false;
    }

    Optional<PriceProviderConfigEntity> providerOpt = priceProviderConfigRepository.findByIdAndEnabledTrue(target.getProviderConfigId());
    if (providerOpt.isEmpty()) {
      log.warn("price.ingest.provider_disabled_or_missing targetId={} providerConfigId={}", target.getId(), target.getProviderConfigId());
      return false;
    }
    String providerName = providerOpt.get().getProviderName();

    if (shouldSkipByPollInterval(target, providerName)) {
      return false;
    }

    PriceInstType instType;
    try {
      instType = PriceInstType.fromValue(target.getInstType());
    } catch (Exception ex) {
      log.warn("price.ingest.invalid_inst_type targetId={} instType={} error={}", target.getId(), target.getInstType(), ex.getMessage());
      return false;
    }

    String quoteSymbol = target.getQuoteSymbol().trim().toUpperCase(Locale.ROOT);
    String baseSymbol = resolveBaseSymbol(target.getInstId(), quoteSymbol);
    if (!StringUtils.hasText(baseSymbol)) {
      log.warn("price.ingest.invalid_inst_id targetId={} instId={} quoteSymbol={}", target.getId(), target.getInstId(), quoteSymbol);
      return false;
    }

    PriceQuery query = new PriceQuery(
      "OFFCHAIN",
      instType,
      baseSymbol,
      quoteSymbol,
      null
    );

    Optional<PriceQuote> quoteOpt = priceService.getQuote(query);
    if (quoteOpt.isEmpty()) {
      log.warn("price.ingest.fetch_empty targetId={} assetId={} instId={} provider={}",
        target.getId(),
        target.getAssetId(),
        target.getInstId(),
        providerName);
      return false;
    }

    PriceQuote quote = quoteOpt.get();
    LocalDateTime quotedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(quote.ts()), ZoneOffset.UTC);
    LocalDateTime bucketTs = quotedAt.truncatedTo(ChronoUnit.MINUTES);

    priceSnapshotService.upsertMinuteSnapshot(new PriceSnapshotUpsertCommand(
      target.getAssetId(),
      providerName,
      instType.name(),
      target.getInstId().trim().toUpperCase(Locale.ROOT),
      quoteSymbol,
      quote.price(),
      bucketTs,
      quotedAt
    ));

    log.info("price.ingest.snapshot_saved targetId={} assetId={} provider={} instId={} price={}",
      target.getId(),
      target.getAssetId(),
      providerName,
      target.getInstId(),
      quote.price());
    return true;
  }

  private boolean shouldSkipByPollInterval(PricePullTargetEntity target, String providerName) {
    Integer pollIntervalMs = target.getPollIntervalMs();
    if (pollIntervalMs == null || pollIntervalMs <= 0) {
      return false;
    }

    Optional<com.chainsentinel.infra.entity.AssetPriceSnapshotEntity> latestOpt =
      assetPriceSnapshotRepository.findTopByAssetIdAndProviderNameAndInstIdOrderByBucketTsDesc(
        target.getAssetId(),
        providerName,
        target.getInstId().trim().toUpperCase(Locale.ROOT)
      );
    if (latestOpt.isEmpty()) {
      return false;
    }

    LocalDateTime latestBucket = latestOpt.get().getBucketTs();
    long elapsedMs = ChronoUnit.MILLIS.between(latestBucket, LocalDateTime.now(ZoneOffset.UTC));
    return elapsedMs < pollIntervalMs;
  }

  private String resolveBaseSymbol(String instId, String quoteSymbol) {
    if (!StringUtils.hasText(instId)) {
      return null;
    }
    String normalized = instId.trim().toUpperCase(Locale.ROOT);
    String suffix = "-" + quoteSymbol;
    if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
      return normalized.substring(0, normalized.length() - suffix.length());
    }
    int firstDash = normalized.indexOf('-');
    if (firstDash > 0) {
      return normalized.substring(0, firstDash);
    }
    return normalized;
  }
}
