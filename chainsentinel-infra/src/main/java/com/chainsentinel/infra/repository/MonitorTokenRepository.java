package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.MonitorTokenEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitorTokenRepository extends JpaRepository<MonitorTokenEntity, Long> {

	Optional<MonitorTokenEntity> findByChainAndTokenContract(String chain, String tokenContract);

	List<MonitorTokenEntity> findByChainAndEnabledTrue(String chain);

	@Query("""
		select t
		from MonitorTokenEntity t
		where (:chain is null or t.chain = :chain)
		  and (:enabled is null or t.enabled = :enabled)
		  and (
		    :keyword is null
		    or lower(t.tokenContract) like concat('%', :keyword, '%')
		    or lower(coalesce(t.symbol, '')) like concat('%', :keyword, '%')
		  )
		order by t.id desc
		""")
	List<MonitorTokenEntity> listByFilters(
		@Param("chain") String chain,
		@Param("keyword") String keyword,
		@Param("enabled") Boolean enabled,
		Pageable pageable
	);
}