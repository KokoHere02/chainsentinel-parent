package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.PriceSnapshotService;
import com.chainsentinel.core.service.dto.PriceSnapshotUpsertCommand;
import com.chainsentinel.core.service.dto.PriceSnapshotView;
import com.chainsentinel.infra.entity.AssetPriceSnapshotEntity;
import com.chainsentinel.infra.repository.AssetPriceSnapshotRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultPriceSnapshotService implements PriceSnapshotService {

  private final AssetPriceSnapshotRepository assetPriceSnapshotRepository;

  public DefaultPriceSnapshotService(AssetPriceSnapshotRepository assetPriceSnapshotRepository) {
    this.assetPriceSnapshotRepository = assetPriceSnapshotRepository;
  }

  @Override
  @Transactional
  public PriceSnapshotView upsertMinuteSnapshot(PriceSnapshotUpsertCommand command) {
    AssetPriceSnapshotEntity entity = assetPriceSnapshotRepository
      .findByAssetIdAndProviderNameAndInstTypeAndInstIdAndBucketTs(
        command.assetId(),
        command.providerName(),
        command.instType(),
        command.instId(),
        command.bucketTs()
      )
      .orElseGet(AssetPriceSnapshotEntity::new);

    entity.setAssetId(command.assetId());
    entity.setProviderName(command.providerName());
    entity.setInstType(command.instType());
    entity.setInstId(command.instId());
    entity.setQuoteSymbol(command.quoteSymbol());
    entity.setPrice(command.price());
    entity.setBucketTs(command.bucketTs());
    entity.setQuotedAt(command.quotedAt());

    AssetPriceSnapshotEntity saved = assetPriceSnapshotRepository.save(entity);
    return toView(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PriceSnapshotView> findLatestByAssetId(Long assetId) {
    return assetPriceSnapshotRepository.findTopByAssetIdOrderByBucketTsDesc(assetId)
      .map(this::toView);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PriceSnapshotView> findRecentByProviderAndInstId(
    String providerName,
    String instId,
    LocalDateTime from,
    LocalDateTime to,
    int limit
  ) {
    List<AssetPriceSnapshotEntity> rows = assetPriceSnapshotRepository
      .findTop200ByProviderNameAndInstIdAndBucketTsBetweenOrderByBucketTsDesc(providerName, instId, from, to);
    int safeLimit = Math.max(1, limit);
    return rows.stream().limit(safeLimit).map(this::toView).toList();
  }

  private PriceSnapshotView toView(AssetPriceSnapshotEntity entity) {
    return new PriceSnapshotView(
      entity.getId(),
      entity.getAssetId(),
      entity.getProviderName(),
      entity.getInstType(),
      entity.getInstId(),
      entity.getQuoteSymbol(),
      entity.getPrice(),
      entity.getBucketTs(),
      entity.getQuotedAt(),
      entity.getFetchedAt()
    );
  }
}