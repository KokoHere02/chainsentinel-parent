package com.chainsentinel.web.auth;

import com.chainsentinel.infra.entity.AuthAuditLogEntity;
import com.chainsentinel.infra.repository.AuthAuditLogRepository;
import com.chainsentinel.infra.repository.AuthAuditLogRepository.OrderCreateActionCountRow;
import com.chainsentinel.infra.repository.AuthAuditLogRepository.OrderCreateRejectCodeCountRow;
import com.chainsentinel.infra.repository.AuthAuditLogRepository.OrderCreateTrendRow;
import com.chainsentinel.infra.support.ManagementQueryPageSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminTradeAuditService {

	private static final String ACTION_ORDER_CREATE_SUCCESS = "ORDER_CREATE_SUCCESS";
	private static final String ACTION_ORDER_CREATE_FAIL = "ORDER_CREATE_FAIL";
	private static final String REQUEST_PATH = "/api/orders";
	private static final String REQUEST_METHOD = "POST";

	private final AuthAuditLogRepository authAuditLogRepository;

	public AdminTradeAuditService(AuthAuditLogRepository authAuditLogRepository) {
		this.authAuditLogRepository = authAuditLogRepository;
	}

	@Transactional(readOnly = true)
	public List<OrderCreateAuditView> listOrderCreateAudits(
		String result,
		String username,
		String rejectCode,
		Instant from,
		Instant to,
		int page,
		int size
	) {
		PageRequest pageable = ManagementQueryPageSupport.pageByIdDesc(page, size);
		Specification<AuthAuditLogEntity> spec = buildOrderCreateSpec(result, username, rejectCode, from, to);
		return authAuditLogRepository.findAll(spec, pageable).stream()
			.map(this::toOrderCreateAuditView)
			.toList();
	}

	@Transactional(readOnly = true)
	public OrderCreateAuditSummaryView summarizeOrderCreateAudits(
		String username,
		String rejectCode,
		Instant from,
		Instant to,
		int top
	) {
		String normalizedUsername = normalizeLower(username);
		String normalizedRejectCode = normalizeUpper(rejectCode);
		long successCount = 0L;
		long rejectCount = 0L;
		for (OrderCreateActionCountRow row : authAuditLogRepository.countOrderCreateActions(normalizedUsername, from, to)) {
			if (row == null || row.getTotal() == null) {
				continue;
			}
			if (ACTION_ORDER_CREATE_SUCCESS.equals(row.getAction())) {
				successCount = row.getTotal();
			} else if (ACTION_ORDER_CREATE_FAIL.equals(row.getAction())) {
				rejectCount = row.getTotal();
			}
		}
		long totalCount = successCount + rejectCount;
		List<RejectCodeCountView> topRejectCodes = authAuditLogRepository.topOrderCreateRejectCodes(
			normalizedUsername,
			normalizedRejectCode,
			from,
			to,
			Math.max(1, Math.min(20, top))
		).stream()
			.filter(row -> row != null && StringUtils.hasText(row.getRejectCode()) && row.getTotal() != null)
			.map(row -> new RejectCodeCountView(row.getRejectCode(), row.getTotal()))
			.toList();
		return new OrderCreateAuditSummaryView(
			totalCount,
			successCount,
			rejectCount,
			calculateRejectRate(totalCount, rejectCount),
			topRejectCodes
		);
	}

	@Transactional(readOnly = true)
	public List<OrderCreateTrendPointView> trendOrderCreateAudits(
		String username,
		Instant from,
		Instant to,
		long bucketSec
	) {
		Instant safeTo = to == null ? Instant.now() : to;
		Instant safeFrom = from == null ? safeTo.minusSeconds(86_400L) : from;
		long safeBucketSec = Math.max(60L, Math.min(86_400L, bucketSec));
		if (!safeFrom.isBefore(safeTo)) {
			return List.of();
		}
		Map<Long, BucketAccumulator> bucketMap = new LinkedHashMap<>();
		long startBucketMs = alignBucketMs(safeFrom.toEpochMilli(), safeBucketSec);
		long endExclusiveMs = safeTo.toEpochMilli();
		for (long bucketMs = startBucketMs; bucketMs < endExclusiveMs; bucketMs += safeBucketSec * 1000L) {
			bucketMap.put(bucketMs, new BucketAccumulator(bucketMs));
		}
		for (OrderCreateTrendRow row : authAuditLogRepository.countOrderCreateTrend(normalizeLower(username), safeFrom, safeTo, safeBucketSec)) {
			if (row == null || row.getBucketStartTs() == null || row.getTotal() == null) {
				continue;
			}
			BucketAccumulator bucket = bucketMap.computeIfAbsent(row.getBucketStartTs(), BucketAccumulator::new);
			if (ACTION_ORDER_CREATE_SUCCESS.equals(row.getAction())) {
				bucket.successCount = row.getTotal();
			} else if (ACTION_ORDER_CREATE_FAIL.equals(row.getAction())) {
				bucket.rejectCount = row.getTotal();
			}
		}
		return bucketMap.values().stream()
			.map(bucket -> new OrderCreateTrendPointView(
				bucket.bucketStartTs,
				bucket.successCount + bucket.rejectCount,
				bucket.successCount,
				bucket.rejectCount,
				calculateRejectRate(bucket.successCount + bucket.rejectCount, bucket.rejectCount)
			))
			.toList();
	}

	private Specification<AuthAuditLogEntity> buildOrderCreateSpec(
		String result,
		String username,
		String rejectCode,
		Instant from,
		Instant to
	) {
		return (root, query, cb) -> {
			List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
			jakarta.persistence.criteria.Predicate successOrFail = cb.or(
				cb.equal(root.get("action"), ACTION_ORDER_CREATE_SUCCESS),
				cb.equal(root.get("action"), ACTION_ORDER_CREATE_FAIL)
			);
			predicates.add(successOrFail);
			predicates.add(cb.equal(root.get("requestPath"), REQUEST_PATH));
			predicates.add(cb.equal(root.get("requestMethod"), REQUEST_METHOD));

			String normalizedResult = normalizeUpper(result);
			if (StringUtils.hasText(normalizedResult)) {
				predicates.add(cb.equal(root.get("result"), normalizedResult));
			}

			String normalizedUsername = normalizeLower(username);
			if (StringUtils.hasText(normalizedUsername)) {
				predicates.add(cb.equal(cb.lower(root.get("username")), normalizedUsername));
			}

			String normalizedRejectCode = normalizeUpper(rejectCode);
			if (StringUtils.hasText(normalizedRejectCode)) {
				predicates.add(cb.equal(root.get("action"), ACTION_ORDER_CREATE_FAIL));
				predicates.add(cb.like(root.get("reason"), "code=" + normalizedRejectCode + ",%"));
			}

			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
			}
			if (to != null) {
				predicates.add(cb.lessThan(root.get("createdAt"), to));
			}

			return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
		};
	}

	private OrderCreateAuditView toOrderCreateAuditView(AuthAuditLogEntity entity) {
		String rejectCode = extractReasonValue(entity.getReason(), "code");
		String accountId = extractReasonValue(entity.getReason(), "accountId");
		String orderId = extractReasonValue(entity.getReason(), "orderId");
		String symbol = extractReasonValue(entity.getReason(), "symbol");
		String status = extractReasonValue(entity.getReason(), "status");
		return new OrderCreateAuditView(
			entity.getId(),
			entity.getAction(),
			entity.getResult(),
			entity.getUserId(),
			entity.getUsername(),
			rejectCode,
			parseLong(accountId),
			parseLong(orderId),
			symbol,
			status,
			entity.getReason(),
			entity.getTraceId(),
			entity.getRequestIp(),
			entity.getCreatedAt()
		);
	}

	private String extractReasonValue(String reason, String key) {
		if (!StringUtils.hasText(reason) || !StringUtils.hasText(key)) {
			return null;
		}
		String prefix = key + "=";
		for (String token : reason.split(",")) {
			String trimmed = token == null ? null : token.trim();
			if (trimmed != null && trimmed.startsWith(prefix)) {
				String value = trimmed.substring(prefix.length()).trim();
				return value.isEmpty() ? null : value;
			}
		}
		return null;
	}

	private Long parseLong(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return Long.valueOf(value);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private String normalizeUpper(String value) {
		return StringUtils.hasText(value) ? value.trim().toUpperCase(java.util.Locale.ROOT) : null;
	}

	private String normalizeLower(String value) {
		return StringUtils.hasText(value) ? value.trim().toLowerCase(java.util.Locale.ROOT) : null;
	}

	public record OrderCreateAuditView(
		Long id,
		String action,
		String result,
		Long userId,
		String username,
		String rejectCode,
		Long accountId,
		Long orderId,
		String symbol,
		String orderStatus,
		String reason,
		String traceId,
		String requestIp,
		Instant createdAt
	) {
	}

	public record OrderCreateAuditSummaryView(
		long totalCount,
		long successCount,
		long rejectCount,
		BigDecimal rejectRate,
		List<RejectCodeCountView> topRejectCodes
	) {
	}

	public record RejectCodeCountView(
		String rejectCode,
		long total
	) {
	}

	public record OrderCreateTrendPointView(
		long bucketStartTs,
		long totalCount,
		long successCount,
		long rejectCount,
		BigDecimal rejectRate
	) {
	}

	private BigDecimal calculateRejectRate(long totalCount, long rejectCount) {
		if (totalCount <= 0L) {
			return BigDecimal.ZERO;
		}
		return BigDecimal.valueOf(rejectCount)
			.divide(BigDecimal.valueOf(totalCount), 6, RoundingMode.HALF_UP);
	}

	private long alignBucketMs(long epochMs, long bucketSec) {
		long bucketMs = bucketSec * 1000L;
		return (epochMs / bucketMs) * bucketMs;
	}

	private static final class BucketAccumulator {
		private final long bucketStartTs;
		private long successCount;
		private long rejectCount;

		private BucketAccumulator(long bucketStartTs) {
			this.bucketStartTs = bucketStartTs;
		}
	}
}
