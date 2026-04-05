package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.RuleTriggerStateEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleTriggerStateRepository extends JpaRepository<RuleTriggerStateEntity, Long> {

Optional<RuleTriggerStateEntity> findByRuleIdAndTargetKey(Long ruleId, String targetKey);
}
