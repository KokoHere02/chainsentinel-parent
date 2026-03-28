package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.MonitorAddressEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitorAddressRepository extends JpaRepository<MonitorAddressEntity, Long> {

    Optional<MonitorAddressEntity> findByChainAndAddress(String chain, String address);

    boolean existsByChainAndAddressAndEnabledTrue(String chain, String address);
}
