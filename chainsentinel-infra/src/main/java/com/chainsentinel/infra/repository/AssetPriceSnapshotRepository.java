package com.chainsentinel.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.chainsentinel.infra.entity.AssetPriceSnapshotEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	Optional<AssetPriceSnapshotEntity> findTopByProviderNameAndInstIdAndBucketTsGreaterThanEqualOrderByBucketTsAsc(
		String providerName,
		String instId,
		LocalDateTime bucketTs
	);

	@Query(value = """
		with ranked as (
			select s.*,
			       row_number() over (partition by s.inst_id order by s.bucket_ts desc, s.id desc) as rn
			from asset_price_snapshot s
			where s.inst_id in (:instIds)
		)
		select *
		from ranked
		where rn = 1
		""", nativeQuery = true)
	List<AssetPriceSnapshotEntity> findLatestByInstIdIn(@Param("instIds") List<String> instIds);

	@Query(value = """
		with ranked as (
			select s.*,
			       row_number() over (partition by s.inst_id order by s.bucket_ts desc, s.id desc) as rn
			from asset_price_snapshot s
			where s.provider_name = :providerName
			  and s.inst_id in (:instIds)
		)
		select *
		from ranked
		where rn = 1
		""", nativeQuery = true)
	List<AssetPriceSnapshotEntity> findLatestByProviderNameAndInstIdIn(
		@Param("providerName") String providerName,
		@Param("instIds") List<String> instIds
	);

}
