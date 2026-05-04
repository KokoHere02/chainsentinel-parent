package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.TradeAccountEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeAccountRepository extends JpaRepository<TradeAccountEntity, Long> {

	Optional<TradeAccountEntity> findByProviderAndName(String provider, String name);

	List<TradeAccountEntity> findByEnabledTrue();

	@Query("""
		select t
		from TradeAccountEntity t
		where (:enabled is null or t.enabled = :enabled)
		  and (:provider is null or lower(t.provider) = :provider)
		  and (
		    :keyword is null
		    or lower(t.name) like concat('%', :keyword, '%')
		    or lower(t.provider) like concat('%', :keyword, '%')
		    or lower(coalesce(t.remark, '')) like concat('%', :keyword, '%')
		  )
		order by t.id desc
		""")
	List<TradeAccountEntity> listByFilters(
		@Param("enabled") Boolean enabled,
		@Param("provider") String provider,
		@Param("keyword") String keyword,
		Pageable pageable
	);
}
