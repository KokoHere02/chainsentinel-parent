package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.MonitorAddressEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitorAddressRepository extends JpaRepository<MonitorAddressEntity, Long> {

	Optional<MonitorAddressEntity> findByChainAndAddress(String chain, String address);

	boolean existsByChainAndAddressAndEnabledTrue(String chain, String address);

	List<MonitorAddressEntity> findByChainAndEnabledTrue(String chain);

	List<MonitorAddressEntity> findByEnabledTrue();

	@Query("""
		select m
		from MonitorAddressEntity m
		where (:chain is null or m.chain = :chain)
		  and (:enabledOnly = false or m.enabled = true)
		  and (
		    :keyword is null
		    or lower(m.address) like concat('%', :keyword, '%')
		    or lower(coalesce(m.tag, '')) like concat('%', :keyword, '%')
		  )
		order by m.id desc
		""")
	List<MonitorAddressEntity> search(
		@Param("chain") String chain,
		@Param("keyword") String keyword,
		@Param("enabledOnly") boolean enabledOnly,
		Pageable pageable
	);

	@Query("""
		select m
		from MonitorAddressEntity m
		where (:chain is null or m.chain = :chain)
		  and (:enabled is null or m.enabled = :enabled)
		  and (
		    :keyword is null
		    or lower(m.address) like concat('%', :keyword, '%')
		    or lower(coalesce(m.tag, '')) like concat('%', :keyword, '%')
		  )
		order by m.id desc
		""")
	List<MonitorAddressEntity> listByFilters(
		@Param("chain") String chain,
		@Param("keyword") String keyword,
		@Param("enabled") Boolean enabled,
		Pageable pageable
	);
}