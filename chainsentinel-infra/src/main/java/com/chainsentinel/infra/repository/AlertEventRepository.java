package com.chainsentinel.infra.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.repository.projection.AlertFailureSummaryProjection;
import com.chainsentinel.infra.repository.projection.RuleHitCountProjection;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertEventRepository extends JpaRepository<AlertEventEntity, Long>, JpaSpecificationExecutor<AlertEventEntity> {

	boolean existsByRuleIdAndAssetEventId(Long ruleId, Long assetEventId);

	List<AlertEventEntity> findTop100BySendStatusOrderByIdAsc(String sendStatus);

	@Query("""
		select ae from AlertEventEntity ae
		where ae.sendStatus in :sendStatuses
		order by ae.id asc
		""")
	List<AlertEventEntity> findBySendStatusInOrderByIdAsc(
		@Param("sendStatuses") List<String> sendStatuses,
		Pageable pageable
	);

	Optional<AlertEventEntity> findTopBySendStatusNotOrderByCreatedAtDesc(String sendStatus);

	@Query("""
select ae.ruleId as ruleId, count(ae) as hitCount
from AlertEventEntity ae
where ae.createdAt >= :since
group by ae.ruleId
""")
	List<RuleHitCountProjection> countHitsByRuleSince(@Param("since") Instant since);

	@Query("""
select ae.sendStatus as sendStatus, ae.lastError as lastError, count(ae) as failureCount
from AlertEventEntity ae
where ae.createdAt >= :since and ae.sendStatus <> 'SENT'
group by ae.sendStatus, ae.lastError
""")
	List<AlertFailureSummaryProjection> summarizeFailuresSince(@Param("since") Instant since);
}
