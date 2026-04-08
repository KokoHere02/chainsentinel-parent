package com.chainsentinel.infra.repository;

import java.util.Optional;

import com.chainsentinel.infra.entity.RuleTriggerStateEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleTriggerStateRepository extends JpaRepository<RuleTriggerStateEntity, Long> {

	Optional<RuleTriggerStateEntity> findByRuleIdAndTargetKey(Long ruleId, String targetKey);

}
