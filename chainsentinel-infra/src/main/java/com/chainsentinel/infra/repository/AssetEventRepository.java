package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.AssetEventEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AssetEventRepository extends JpaRepository<AssetEventEntity, Long>, JpaSpecificationExecutor<AssetEventEntity> {

    Optional<AssetEventEntity> findByChainAndTxHashAndLogIndex(String chain, String txHash, Integer logIndex);
}
