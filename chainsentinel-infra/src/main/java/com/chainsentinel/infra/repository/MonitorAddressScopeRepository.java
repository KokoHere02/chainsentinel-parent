package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitorAddressScopeRepository extends JpaRepository<MonitorAddressScopeEntity, Long> {

	Optional<MonitorAddressScopeEntity> findByMonitorAddressIdAndChainAndNetwork(Long monitorAddressId, String chain,
		String network);

	List<MonitorAddressScopeEntity> findByEnabledTrue();

	List<MonitorAddressScopeEntity> findByMonitorAddressId(Long monitorAddressId);

	List<MonitorAddressScopeEntity> findByMonitorAddressIdInOrderByMonitorAddressIdAscIdAsc(List<Long> monitorAddressIds);

	@Query("""
		select s
		from MonitorAddressScopeEntity s
		where (:monitorAddressId is null or s.monitorAddressId = :monitorAddressId)
		  and (:chain is null or s.chain = :chain)
		  and (:network is null or s.network = :network)
		  and (:enabled is null or s.enabled = :enabled)
		order by s.id desc
		""")
	List<MonitorAddressScopeEntity> listByFilters(
		@Param("monitorAddressId") Long monitorAddressId,
		@Param("chain") String chain,
		@Param("network") String network,
		@Param("enabled") Boolean enabled,
		Pageable pageable
	);
}
