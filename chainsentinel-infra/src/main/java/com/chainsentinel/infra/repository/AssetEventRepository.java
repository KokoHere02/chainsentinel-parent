package com.chainsentinel.infra.repository;

import java.util.List;
import java.util.Optional;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.infra.entity.AssetEventEntity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AssetEventRepository extends JpaRepository<AssetEventEntity, Long>,
	JpaSpecificationExecutor<AssetEventEntity> {

	Optional<AssetEventEntity> findByChainAndTxHashAndLogIndex(String chain, String txHash, Integer logIndex);

	long countByChainAndNetworkAndStatus(String chain, String network, EventStatus status);

	List<AssetEventEntity> findByChainAndNetworkAndStatusAndIdGreaterThanOrderByIdAsc(
		String chain,
		String network,
		EventStatus status,
		Long id,
		Pageable pageable
	);

}
