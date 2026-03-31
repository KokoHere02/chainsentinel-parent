package com.chainsentinel.infra.repository;

import java.util.List;
import java.util.Optional;

import com.chainsentinel.infra.entity.ChainConfigEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChainConfigRepository extends JpaRepository<ChainConfigEntity, Long> {

    Optional<ChainConfigEntity> findByChainAndNetwork(String chain, String network);

    List<ChainConfigEntity> findByChainAndEnabledTrue(String chain);

  List<ChainConfigEntity> findByEnabledTrue();
}
