package com.chainsentinel.infra.repository;

import java.util.List;
import java.util.Optional;

import com.chainsentinel.infra.entity.PriceTickEntity;
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

	@Modifying
	@Query("delete from PriceTickEntity t where t.quoteTs < :cutoffTs")
	int deleteByQuoteTsBefore(@Param("cutoffTs") Long cutoffTs);

}
