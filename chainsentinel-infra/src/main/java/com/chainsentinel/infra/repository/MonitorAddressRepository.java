package com.chainsentinel.infra.repository;

import java.util.List;
import java.util.Optional;

import com.chainsentinel.infra.entity.MonitorAddressEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitorAddressRepository extends JpaRepository<MonitorAddressEntity, Long> {

    Optional<MonitorAddressEntity> findByChainAndAddress(String chain, String address);

    boolean existsByChainAndAddressAndEnabledTrue(String chain, String address);

    List<MonitorAddressEntity> findByChainAndEnabledTrue(String chain);

    List<MonitorAddressEntity> findByEnabledTrue();
}
