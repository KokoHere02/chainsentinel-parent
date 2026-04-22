package com.chainsentinel.infra.repository;

import java.util.List;
import java.util.Optional;

import com.chainsentinel.infra.entity.PriceProviderConfigEntity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceProviderConfigRepository extends JpaRepository<PriceProviderConfigEntity, Long> {

	Optional<PriceProviderConfigEntity> findByIdAndEnabledTrue(Long id);

	Optional<PriceProviderConfigEntity> findByProviderNameAndEnabledTrue(String providerName);

	List<PriceProviderConfigEntity> findByEnabledTrueOrderByPriorityAscIdAsc();

	@Query("""
		select p
		from PriceProviderConfigEntity p
		where (:enabled is null or p.enabled = :enabled)
		  and (:keyword is null or lower(p.providerName) like concat('%', :keyword, '%') or lower(p.baseUrl) like concat('%', :keyword, '%'))
		order by p.priority asc, p.id asc
		""")
	List<PriceProviderConfigEntity> listByFilters(
		@Param("enabled") Boolean enabled,
		@Param("keyword") String keyword,
		Pageable pageable
	);

}
