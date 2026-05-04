package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.TradeAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeAuditLogRepository extends JpaRepository<TradeAuditLogEntity, Long> {
}
