package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.repository.projection.RuleHitCountProjection;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertEventRepository extends JpaRepository<AlertEventEntity, Long>, JpaSpecificationExecutor<AlertEventEntity> {

boolean existsByRuleIdAndAssetEventId(Long ruleId, Long assetEventId);

List<AlertEventEntity> findTop100BySendStatusOrderByIdAsc(String sendStatus);

@Query("""
select ae.ruleId as ruleId, count(ae) as hitCount
from AlertEventEntity ae
where ae.createdAt >= :since
group by ae.ruleId
""")
List<RuleHitCountProjection> countHitsByRuleSince(@Param("since") Instant since);
}
