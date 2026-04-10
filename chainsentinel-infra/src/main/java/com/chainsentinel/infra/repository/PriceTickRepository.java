package com.chainsentinel.infra.repository;

import java.util.Optional;

import com.chainsentinel.infra.entity.PriceTickEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceTickRepository extends JpaRepository<PriceTickEntity, Long> {

	Optional<PriceTickEntity> findTopByProviderNameAndInstIdOrderByQuoteTsDesc(
		String providerName,
		String instId
	);

}

