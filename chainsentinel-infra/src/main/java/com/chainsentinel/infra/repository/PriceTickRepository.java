package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.PriceTickEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceTickRepository extends JpaRepository<PriceTickEntity, Long> {

	Optional<PriceTickEntity> findTopByProviderNameAndInstIdOrderByQuoteTsDesc(
		String providerName,
		String instId
	);

	@Query("""
		select t
		from PriceTickEntity t
		where (:providerName is null or t.providerName = :providerName)
		  and (:instId is null or t.instId = :instId)
		  and (:fromTs is null or t.quoteTs >= :fromTs)
		  and (:toTs is null or t.quoteTs <= :toTs)
		order by t.quoteTs desc
		""")
	List<PriceTickEntity> queryTicks(
		@Param("providerName") String providerName,
		@Param("instId") String instId,
		@Param("fromTs") Long fromTs,
		@Param("toTs") Long toTs,
		Pageable pageable
	);

	@Query(value = """
		select t.id,
		       t.provider_name,
		       t.inst_type,
		       t.inst_id,
		       t.base_symbol,
		       t.quote_symbol,
		       t.price,
		       t.quote_ts,
		       t.ingested_at
		from price_tick t
		where t.provider_name = :providerName
		  and t.inst_id = :instId
		  and (:fromTs is null or t.quote_ts >= :fromTs)
		  and (:toTs is null or t.quote_ts <= :toTs)
		order by t.quote_ts desc
		limit :tickLimit
		""", nativeQuery = true)
	List<PriceTickEntity> queryTicksByProviderAndInst(
		@Param("providerName") String providerName,
		@Param("instId") String instId,
		@Param("fromTs") Long fromTs,
		@Param("toTs") Long toTs,
		@Param("tickLimit") int tickLimit
	);

	@Query(value = """
		select t.id,
		       t.provider_name,
		       t.inst_type,
		       t.inst_id,
		       t.base_symbol,
		       t.quote_symbol,
		       t.price,
		       t.quote_ts,
		       t.ingested_at
		from price_tick t
		where t.provider_name = :providerName
		  and t.inst_id = :instId
		  and t.quote_ts >= :fromTs
		order by t.quote_ts asc
		limit 1
		""", nativeQuery = true)
	Optional<PriceTickEntity> queryEarliestTickSince(
		@Param("providerName") String providerName,
		@Param("instId") String instId,
		@Param("fromTs") Long fromTs
	);

	@Query(value = """
		select agg.bucket_start_ts as bucketStartTs,
		       max(case when agg.rn = 1 then agg.price end) as lastPrice,
		       min(agg.price) as minPrice,
		       max(agg.price) as maxPrice,
		       count(*) as count
		from (
			select floor(t.quote_ts / :bucketMs) * :bucketMs as bucket_start_ts,
			       t.price as price,
			       row_number() over (
					partition by floor(t.quote_ts / :bucketMs)
					order by t.quote_ts desc
			   ) as rn
			from price_tick t
			where (:providerName is null or t.provider_name = :providerName)
			  and (:instId is null or t.inst_id = :instId)
			  and (:fromTs is null or t.quote_ts >= :fromTs)
			  and (:toTs is null or t.quote_ts <= :toTs)
		) agg
		group by agg.bucket_start_ts
		order by agg.bucket_start_ts desc
		limit :bucketLimit
		""", nativeQuery = true)
	List<PriceTickAggregateRow> queryTickAggregates(
		@Param("providerName") String providerName,
		@Param("instId") String instId,
		@Param("fromTs") Long fromTs,
		@Param("toTs") Long toTs,
		@Param("bucketMs") long bucketMs,
		@Param("bucketLimit") int bucketLimit
	);

	@Query(value = """
		select agg.bucket_start_ts as bucketStartTs,
		       max(case when agg.rn = 1 then agg.price end) as lastPrice,
		       min(agg.price) as minPrice,
		       max(agg.price) as maxPrice,
		       count(*) as count
		from (
			select floor(t.quote_ts / :bucketMs) * :bucketMs as bucket_start_ts,
			       t.price as price,
			       row_number() over (
					partition by floor(t.quote_ts / :bucketMs)
					order by t.quote_ts desc
			   ) as rn
			from price_tick t
			where t.provider_name = :providerName
			  and t.inst_id = :instId
			  and (:fromTs is null or t.quote_ts >= :fromTs)
			  and (:toTs is null or t.quote_ts <= :toTs)
		) agg
		group by agg.bucket_start_ts
		order by agg.bucket_start_ts desc
		limit :bucketLimit
		""", nativeQuery = true)
	List<PriceTickAggregateRow> queryTickAggregatesByProviderAndInst(
		@Param("providerName") String providerName,
		@Param("instId") String instId,
		@Param("fromTs") Long fromTs,
		@Param("toTs") Long toTs,
		@Param("bucketMs") long bucketMs,
		@Param("bucketLimit") int bucketLimit
	);

	@Modifying
	@Query("delete from PriceTickEntity t where t.quoteTs < :cutoffTs")
	int deleteByQuoteTsBefore(@Param("cutoffTs") Long cutoffTs);

	interface PriceTickAggregateRow {
		Long getBucketStartTs();

		BigDecimal getLastPrice();

		BigDecimal getMinPrice();

		BigDecimal getMaxPrice();

		Long getCount();
	}
}
