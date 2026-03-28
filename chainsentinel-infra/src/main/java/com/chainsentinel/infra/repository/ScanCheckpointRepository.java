package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.ScanCheckpointEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanCheckpointRepository extends JpaRepository<ScanCheckpointEntity, Long> {

    Optional<ScanCheckpointEntity> findByChainAndNetwork(String chain, String network);
}
