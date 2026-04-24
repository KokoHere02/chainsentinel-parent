package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.MonitorScopeTokenEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitorScopeTokenRepository extends JpaRepository<MonitorScopeTokenEntity, Long> {

	Optional<MonitorScopeTokenEntity> findByMonitorScopeIdAndTokenContract(Long monitorScopeId, String tokenContract);

	List<MonitorScopeTokenEntity> findByMonitorScopeId(Long monitorScopeId);

	List<MonitorScopeTokenEntity> findByMonitorScopeIdInOrderByMonitorScopeIdAscIdAsc(List<Long> monitorScopeIds);

	@Query("""
		select t
		from MonitorScopeTokenEntity t
		where (:monitorScopeId is null or t.monitorScopeId = :monitorScopeId)
		  and (:enabled is null or t.enabled = :enabled)
		  and (
		    :keyword is null
		    or lower(t.tokenContract) like concat('%', :keyword, '%')
		    or lower(coalesce(t.symbol, '')) like concat('%', :keyword, '%')
		  )
		order by t.id desc
		""")
	List<MonitorScopeTokenEntity> listByFilters(
		@Param("monitorScopeId") Long monitorScopeId,
		@Param("keyword") String keyword,
		@Param("enabled") Boolean enabled,
		Pageable pageable
	);
}
