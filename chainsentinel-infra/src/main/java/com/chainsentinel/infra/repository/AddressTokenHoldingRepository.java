package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.AddressTokenHoldingEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AddressTokenHoldingRepository extends JpaRepository<AddressTokenHoldingEntity, Long> {

	Optional<AddressTokenHoldingEntity> findByMonitorScopeIdAndTokenContract(
		Long monitorScopeId,
		String tokenContract
	);

	@Query("""
		select h
		from AddressTokenHoldingEntity h
		where (:chain is null or h.chain = :chain)
		  and (:network is null or h.network = :network)
		  and (:address is null or h.address = :address)
		order by h.balanceUpdatedAt desc, h.id desc
		""")
	List<AddressTokenHoldingEntity> listByFilters(
		@Param("chain") String chain,
		@Param("network") String network,
		@Param("address") String address,
		Pageable pageable
	);
}
