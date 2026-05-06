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

	long countByCreatedAtAfter(Instant since);

	long countByCreatedAtAfterAndSeverityIn(Instant since, List<String> severities);

	@Query("""
		select a.severity as severity, count(a) as total
		from AlertEventEntity a
		where a.createdAt >= :fromAt and a.createdAt <= :toAt
		group by a.severity
		""")
	List<AlertSeverityRow> countBySeverityBetween(
		@Param("fromAt") Instant fromAt,
		@Param("toAt") Instant toAt
	);

	@Query("""
		select a.ruleId as ruleId, count(a) as total
		from AlertEventEntity a
		where a.createdAt >= :fromAt and a.createdAt <= :toAt
		group by a.ruleId
		order by count(a) desc
		""")
	List<AlertRuleCountRow> countByRuleBetween(
		@Param("fromAt") Instant fromAt,
		@Param("toAt") Instant toAt,
		Pageable pageable
	);

	@Query(value = """
		select agg.bucket_key * :bucketSec * 1000 as bucketStartTs,
		       agg.total as total
		from (
			select floor(unix_timestamp(a.created_at) / :bucketSec) as bucket_key,
			       count(*) as total
			from alert_event a
			where a.created_at >= :fromAt
			  and a.created_at <= :toAt
			group by floor(unix_timestamp(a.created_at) / :bucketSec)
		) agg
		order by agg.bucket_key asc
		""", nativeQuery = true)
	List<AlertTrendBucketRow> countTrendByBucketBetween(
		@Param("fromAt") Instant fromAt,
		@Param("toAt") Instant toAt,
		@Param("bucketSec") long bucketSec
	);

	List<AlertEventEntity> findAllByOrderByIdDesc(Pageable pageable);

	interface AlertSeverityRow {
		String getSeverity();

		Long getTotal();
	}

	interface AlertRuleCountRow {
		Long getRuleId();

		Long getTotal();
	}

	interface AlertTrendBucketRow {
		Long getBucketStartTs();

		Long getTotal();
	}
}
