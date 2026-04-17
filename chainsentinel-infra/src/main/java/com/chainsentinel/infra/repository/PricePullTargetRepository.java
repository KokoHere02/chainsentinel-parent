package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.PricePullTargetEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PricePullTargetRepository extends JpaRepository<PricePullTargetEntity, Long> {

	List<PricePullTargetEntity> findByEnabledTrueOrderByPriorityAscIdAsc();

	@Query("""
		select t
		from PricePullTargetEntity t
		where (:providerConfigId is null or t.providerConfigId = :providerConfigId)
		  and (:enabled is null or t.enabled = :enabled)
		  and (:keyword is null or lower(t.instId) like concat('%', :keyword, '%') or lower(t.quoteSymbol) like concat('%', :keyword, '%'))
		order by t.priority asc, t.id asc
		""")
	List<PricePullTargetEntity> listByFilters(
		@Param("providerConfigId") Long providerConfigId,
		@Param("enabled") Boolean enabled,
		@Param("keyword") String keyword,
		Pageable pageable
	);

	@Query("""
		select t
		from PricePullTargetEntity t, PriceProviderConfigEntity p
		where t.providerConfigId = p.id
		  and t.enabled = true
		  and p.enabled = true
		  and lower(p.providerName) = lower(:providerName)
		order by t.priority asc, t.id asc
		""")
	List<PricePullTargetEntity> findEnabledByProviderName(@Param("providerName") String providerName);
}