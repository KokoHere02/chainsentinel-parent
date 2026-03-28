package com.chainsentinel.infra.repository;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleRepository extends JpaRepository<AlertRuleEntity, Long> {

    List<AlertRuleEntity> findByTypeAndEnabledTrue(AlertRuleType type);
}
