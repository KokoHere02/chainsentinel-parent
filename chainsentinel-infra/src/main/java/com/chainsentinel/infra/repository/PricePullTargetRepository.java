package com.chainsentinel.infra.repository;

import java.util.List;

import com.chainsentinel.infra.entity.PricePullTargetEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PricePullTargetRepository extends JpaRepository<PricePullTargetEntity, Long> {

	List<PricePullTargetEntity> findByEnabledTrueOrderByPriorityAscIdAsc();

}
