package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.AuthAuditLogEntity;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLogEntity, Long>, JpaSpecificationExecutor<AuthAuditLogEntity> {

	List<AuthAuditLogEntity> findTop200ByUserIdOrderByIdDesc(Long userId);

	List<AuthAuditLogEntity> findTop50ByUserIdOrderByIdDesc(Long userId);

	@Query(value = """
		select action as action, count(*) as total
		from auth_audit_log
		where action in ('ORDER_CREATE_SUCCESS', 'ORDER_CREATE_FAIL')
		  and request_path = '/api/orders'
		  and request_method = 'POST'
		  and (:username is null or lower(username) = lower(:username))
		  and (:fromAt is null or created_at >= :fromAt)
		  and (:toAt is null or created_at < :toAt)
		group by action
		""", nativeQuery = true)
	List<OrderCreateActionCountRow> countOrderCreateActions(
		@Param("username") String username,
		@Param("fromAt") Instant fromAt,
		@Param("toAt") Instant toAt
	);

	@Query(value = """
		select substring_index(substring_index(reason, ',', 1), '=', -1) as rejectCode, count(*) as total
		from auth_audit_log
		where action = 'ORDER_CREATE_FAIL'
		  and request_path = '/api/orders'
		  and request_method = 'POST'
		  and (:username is null or lower(username) = lower(:username))
		  and (:fromAt is null or created_at >= :fromAt)
		  and (:toAt is null or created_at < :toAt)
		  and (:rejectCode is null or substring_index(substring_index(reason, ',', 1), '=', -1) = :rejectCode)
		group by substring_index(substring_index(reason, ',', 1), '=', -1)
		order by total desc, rejectCode asc
		limit :limit
		""", nativeQuery = true)
	List<OrderCreateRejectCodeCountRow> topOrderCreateRejectCodes(
		@Param("username") String username,
		@Param("rejectCode") String rejectCode,
		@Param("fromAt") Instant fromAt,
		@Param("toAt") Instant toAt,
		@Param("limit") int limit
	);

	@Query(value = """
		select
		  floor(unix_timestamp(created_at) / :bucketSec) * :bucketSec * 1000 as bucketStartTs,
		  action as action,
		  count(*) as total
		from auth_audit_log
		where action in ('ORDER_CREATE_SUCCESS', 'ORDER_CREATE_FAIL')
		  and request_path = '/api/orders'
		  and request_method = 'POST'
		  and (:username is null or lower(username) = lower(:username))
		  and created_at >= :fromAt
		  and created_at < :toAt
		group by floor(unix_timestamp(created_at) / :bucketSec), action
		order by bucketStartTs asc
		""", nativeQuery = true)
	List<OrderCreateTrendRow> countOrderCreateTrend(
		@Param("username") String username,
		@Param("fromAt") Instant fromAt,
		@Param("toAt") Instant toAt,
		@Param("bucketSec") long bucketSec
	);

	interface OrderCreateActionCountRow {
		String getAction();
		Long getTotal();
	}

	interface OrderCreateRejectCodeCountRow {
		String getRejectCode();
		Long getTotal();
	}

	interface OrderCreateTrendRow {
		Long getBucketStartTs();
		String getAction();
		Long getTotal();
	}
}
