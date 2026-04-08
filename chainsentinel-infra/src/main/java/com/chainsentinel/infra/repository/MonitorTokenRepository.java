package com.chainsentinel.infra.repository;

import java.util.List;
import java.util.Optional;

import com.chainsentinel.infra.entity.MonitorTokenEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitorTokenRepository extends JpaRepository<MonitorTokenEntity, Long> {

	Optional<MonitorTokenEntity> findByChainAndTokenContract(String chain, String tokenContract);

	List<MonitorTokenEntity> findByChainAndEnabledTrue(String chain);

}
