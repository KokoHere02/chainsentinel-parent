package com.chainsentinel.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.entity.AuthAuditLogEntity;
import com.chainsentinel.infra.repository.AuthAuditLogRepository;
import com.chainsentinel.infra.repository.AuthAuditLogRepository.OrderCreateActionCountRow;
import com.chainsentinel.infra.repository.AuthAuditLogRepository.OrderCreateRejectCodeCountRow;
import com.chainsentinel.infra.repository.AuthAuditLogRepository.OrderCreateTrendRow;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class AdminTradeAuditServiceTest {

	@Mock
	private AuthAuditLogRepository authAuditLogRepository;

	@Test
	void shouldMapRejectAuditRow() {
		AdminTradeAuditService service = new AdminTradeAuditService(authAuditLogRepository);
		AuthAuditLogEntity entity = new AuthAuditLogEntity();
		org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 11L);
		entity.setAction("ORDER_CREATE_FAIL");
		entity.setResult("FAIL");
		entity.setUserId(1L);
		entity.setUsername("admin");
		entity.setReason("code=TRADE_DISABLED,message=trade is disabled");
		entity.setTraceId("t1");
		entity.setRequestIp("127.0.0.1");
		org.springframework.test.util.ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-08T03:00:00Z"));

		when(authAuditLogRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(entity)));

		List<AdminTradeAuditService.OrderCreateAuditView> result = service.listOrderCreateAudits(
			"FAIL", "admin", "TRADE_DISABLED", null, null, 0, 100
		);

		assertEquals(1, result.size());
		assertEquals("TRADE_DISABLED", result.get(0).rejectCode());
		assertEquals(null, result.get(0).accountId());
		assertEquals(null, result.get(0).orderId());
	}

	@Test
	void shouldMapSuccessAuditRow() {
		AdminTradeAuditService service = new AdminTradeAuditService(authAuditLogRepository);
		AuthAuditLogEntity entity = new AuthAuditLogEntity();
		org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 12L);
		entity.setAction("ORDER_CREATE_SUCCESS");
		entity.setResult("SUCCESS");
		entity.setUserId(2L);
		entity.setUsername("trader");
		entity.setReason("accountId=7,orderId=1001,symbol=BTC-USDT,status=SUBMITTED");
		entity.setTraceId("t2");
		entity.setRequestIp("127.0.0.2");
		org.springframework.test.util.ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-08T03:05:00Z"));

		when(authAuditLogRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(entity)));

		List<AdminTradeAuditService.OrderCreateAuditView> result = service.listOrderCreateAudits(
			"SUCCESS", "trader", null, null, null, 0, 100
		);

		assertEquals(1, result.size());
		assertEquals(7L, result.get(0).accountId());
		assertEquals(1001L, result.get(0).orderId());
		assertEquals("BTC-USDT", result.get(0).symbol());
		assertEquals("SUBMITTED", result.get(0).orderStatus());
	}

	@Test
	void shouldSummarizeOrderCreateAudits() {
		AdminTradeAuditService service = new AdminTradeAuditService(authAuditLogRepository);
		when(authAuditLogRepository.countOrderCreateActions("admin", Instant.parse("2026-05-08T00:00:00Z"), Instant.parse("2026-05-09T00:00:00Z")))
			.thenReturn(List.of(
				actionCountRow("ORDER_CREATE_SUCCESS", 8L),
				actionCountRow("ORDER_CREATE_FAIL", 2L)
			));
		when(authAuditLogRepository.topOrderCreateRejectCodes("admin", null, Instant.parse("2026-05-08T00:00:00Z"), Instant.parse("2026-05-09T00:00:00Z"), 10))
			.thenReturn(List.of(rejectCodeCountRow("TRADE_DISABLED", 2L)));

		AdminTradeAuditService.OrderCreateAuditSummaryView result = service.summarizeOrderCreateAudits(
			"admin",
			null,
			Instant.parse("2026-05-08T00:00:00Z"),
			Instant.parse("2026-05-09T00:00:00Z"),
			10
		);

		assertEquals(10L, result.totalCount());
		assertEquals(8L, result.successCount());
		assertEquals(2L, result.rejectCount());
		assertEquals("0.200000", result.rejectRate().toPlainString());
		assertEquals(1, result.topRejectCodes().size());
		assertEquals("TRADE_DISABLED", result.topRejectCodes().get(0).rejectCode());
	}

	@Test
	void shouldBuildTrendAndFillMissingBuckets() {
		AdminTradeAuditService service = new AdminTradeAuditService(authAuditLogRepository);
		Instant from = Instant.parse("2026-05-08T00:00:00Z");
		Instant to = Instant.parse("2026-05-08T03:00:00Z");
		long bucket0 = from.toEpochMilli();
		long bucket2 = from.plusSeconds(7200L).toEpochMilli();
		when(authAuditLogRepository.countOrderCreateTrend("admin", from, to, 3600L))
			.thenReturn(List.of(
				trendRow(bucket0, "ORDER_CREATE_SUCCESS", 2L),
				trendRow(bucket0, "ORDER_CREATE_FAIL", 1L),
				trendRow(bucket2, "ORDER_CREATE_SUCCESS", 3L)
			));

		List<AdminTradeAuditService.OrderCreateTrendPointView> result = service.trendOrderCreateAudits(
			"admin",
			from,
			to,
			3600L
		);

		assertEquals(3, result.size());
		assertEquals(3L, result.get(0).totalCount());
		assertEquals(2L, result.get(0).successCount());
		assertEquals(1L, result.get(0).rejectCount());
		assertEquals("0.333333", result.get(0).rejectRate().toPlainString());
		assertEquals(0L, result.get(1).totalCount());
		assertEquals(3L, result.get(2).successCount());
		assertEquals(0L, result.get(2).rejectCount());
	}

	private OrderCreateActionCountRow actionCountRow(String action, Long total) {
		return new OrderCreateActionCountRow() {
			@Override
			public String getAction() {
				return action;
			}

			@Override
			public Long getTotal() {
				return total;
			}
		};
	}

	private OrderCreateRejectCodeCountRow rejectCodeCountRow(String rejectCode, Long total) {
		return new OrderCreateRejectCodeCountRow() {
			@Override
			public String getRejectCode() {
				return rejectCode;
			}

			@Override
			public Long getTotal() {
				return total;
			}
		};
	}

	private OrderCreateTrendRow trendRow(Long bucketStartTs, String action, Long total) {
		return new OrderCreateTrendRow() {
			@Override
			public Long getBucketStartTs() {
				return bucketStartTs;
			}

			@Override
			public String getAction() {
				return action;
			}

			@Override
			public Long getTotal() {
				return total;
			}
		};
	}
}
