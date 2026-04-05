package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.PriceProviderConfigEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceProviderConfigRepository extends JpaRepository<PriceProviderConfigEntity, Long> {

Optional<PriceProviderConfigEntity> findByIdAndEnabledTrue(Long id);

Optional<PriceProviderConfigEntity> findByProviderNameAndEnabledTrue(String providerName);

List<PriceProviderConfigEntity> findByEnabledTrueOrderByPriorityAscIdAsc();
}