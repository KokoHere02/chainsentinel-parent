package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.MonitorAddressEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitorAddressRepository extends JpaRepository<MonitorAddressEntity, Long> {

	Optional<MonitorAddressEntity> findByAddress(String address);

	boolean existsByAddressAndEnabledTrue(String address);

	List<MonitorAddressEntity> findByEnabledTrue();

	@Query("""
		select m
		from MonitorAddressEntity m
		where (:enabled is null or m.enabled = :enabled)
		  and (
		    :keyword is null
		    or lower(m.address) like concat('%', :keyword, '%')
		    or lower(coalesce(m.tag, '')) like concat('%', :keyword, '%')
		  )
		order by m.id desc
		""")
	List<MonitorAddressEntity> listByFilters(
		@Param("keyword") String keyword,
		@Param("enabled") Boolean enabled,
		Pageable pageable
	);

	List<MonitorAddressEntity> findByIdInAndEnabledTrue(List<Long> ids);
}
