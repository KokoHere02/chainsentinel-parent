package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.MonitorTokenEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitorTokenRepository extends JpaRepository<MonitorTokenEntity, Long> {

    Optional<MonitorTokenEntity> findByChainAndTokenContract(String chain, String tokenContract);

    List<MonitorTokenEntity> findByChainAndEnabledTrue(String chain);
}
