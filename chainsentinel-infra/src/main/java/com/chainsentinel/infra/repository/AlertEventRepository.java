package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.AlertEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AlertEventRepository extends JpaRepository<AlertEventEntity, Long>, JpaSpecificationExecutor<AlertEventEntity> {

boolean existsByRuleIdAndAssetEventId(Long ruleId, Long assetEventId);

List<AlertEventEntity> findTop100BySendStatusOrderByIdAsc(String sendStatus);
}
