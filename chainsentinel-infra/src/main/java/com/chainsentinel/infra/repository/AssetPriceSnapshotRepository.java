package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.AssetPriceSnapshotEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetPriceSnapshotRepository extends JpaRepository<AssetPriceSnapshotEntity, Long> {

Optional<AssetPriceSnapshotEntity> findByAssetIdAndProviderNameAndInstTypeAndInstIdAndBucketTs(
Long assetId,
String providerName,
String instType,
String instId,
LocalDateTime bucketTs
);

Optional<AssetPriceSnapshotEntity> findTopByAssetIdOrderByBucketTsDesc(Long assetId);

Optional<AssetPriceSnapshotEntity> findTopByInstIdOrderByBucketTsDesc(String instId);

Optional<AssetPriceSnapshotEntity> findTopByAssetIdAndProviderNameAndInstIdOrderByBucketTsDesc(
Long assetId,
String providerName,
String instId
);

List<AssetPriceSnapshotEntity> findTop200ByProviderNameAndInstIdAndBucketTsBetweenOrderByBucketTsDesc(
String providerName,
String instId,
LocalDateTime from,
LocalDateTime to
);
}
