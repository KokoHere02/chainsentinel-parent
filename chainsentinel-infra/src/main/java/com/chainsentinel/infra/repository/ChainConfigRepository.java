package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.ChainConfigEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChainConfigRepository extends JpaRepository<ChainConfigEntity, Long> {

    Optional<ChainConfigEntity> findByChainAndNetwork(String chain, String network);

    List<ChainConfigEntity> findByChainAndEnabledTrue(String chain);
}
