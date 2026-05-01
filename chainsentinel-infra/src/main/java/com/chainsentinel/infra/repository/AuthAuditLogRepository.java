package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.AuthAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLogEntity, Long> {
}
