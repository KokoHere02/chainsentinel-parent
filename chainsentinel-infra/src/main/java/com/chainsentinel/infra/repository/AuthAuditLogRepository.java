package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.AuthAuditLogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLogEntity, Long> {

	List<AuthAuditLogEntity> findTop200ByUserIdOrderByIdDesc(Long userId);

	List<AuthAuditLogEntity> findTop50ByUserIdOrderByIdDesc(Long userId);
}
