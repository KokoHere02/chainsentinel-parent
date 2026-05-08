package com.chainsentinel.infra.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.chainsentinel.infra.entity.AuthAuditLogEntity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
	"spring.datasource.url=jdbc:h2:mem:auth-audit-repo;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthAuditLogRepositoryTest {

	@Autowired
	private AuthAuditLogRepository authAuditLogRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("DROP ALIAS IF EXISTS SUBSTRING_INDEX");
		jdbcTemplate.execute("CREATE ALIAS SUBSTRING_INDEX FOR \"" + SqlFunctions.class.getName() + ".substringIndex\"");
		jdbcTemplate.update("delete from auth_audit_log");
	}

	@Test
	void shouldCountOrderCreateActionsAndRejectCodes() {
		Instant from = Instant.parse("1970-01-20T00:00:00Z");
		Instant to = Instant.parse("1970-01-21T00:00:00Z");
		insertAudit("ORDER_CREATE_SUCCESS", "SUCCESS", "admin", "accountId=1,orderId=1001,symbol=BTC-USDT,status=SUBMITTED", "1970-01-20T01:00:00Z");
		insertAudit("ORDER_CREATE_SUCCESS", "SUCCESS", "admin", "accountId=1,orderId=1002,symbol=ETH-USDT,status=SUBMITTED", "1970-01-20T02:00:00Z");
		insertAudit("ORDER_CREATE_FAIL", "FAIL", "admin", "code=TRADE_DISABLED,message=trade is disabled", "1970-01-20T03:00:00Z");
		insertAudit("ORDER_CREATE_FAIL", "FAIL", "admin", "code=TRADE_DISABLED,message=trade is disabled", "1970-01-20T04:00:00Z");
		insertAudit("ORDER_CREATE_FAIL", "FAIL", "admin", "code=TRADE_RISK_REJECTED,message=order quantity exceeds max limit", "1970-01-20T05:00:00Z");
		insertAudit("ORDER_CREATE_SUCCESS", "SUCCESS", "other", "accountId=2,orderId=1003,symbol=SOL-USDT,status=SUBMITTED", "1970-01-20T06:00:00Z");

		List<AuthAuditLogRepository.OrderCreateActionCountRow> counts = authAuditLogRepository.countOrderCreateActions("admin", from, to);
		assertEquals(2L, totalByAction(counts, "ORDER_CREATE_SUCCESS"));
		assertEquals(3L, totalByAction(counts, "ORDER_CREATE_FAIL"));

		List<AuthAuditLogRepository.OrderCreateRejectCodeCountRow> rejectCodes = authAuditLogRepository.topOrderCreateRejectCodes(
			"admin",
			null,
			from,
			to,
			10
		);
		assertEquals(2, rejectCodes.size());
		assertEquals("TRADE_DISABLED", rejectCodes.get(0).getRejectCode());
		assertEquals(2L, rejectCodes.get(0).getTotal());
		assertEquals("TRADE_RISK_REJECTED", rejectCodes.get(1).getRejectCode());
		assertEquals(1L, rejectCodes.get(1).getTotal());
	}

	@Test
	void shouldBuildOrderCreateTrendByBucket() {
		Instant from = Instant.parse("1970-01-20T00:00:00Z");
		Instant to = Instant.parse("1970-01-20T03:00:00Z");
		insertAudit("ORDER_CREATE_SUCCESS", "SUCCESS", "admin", "accountId=1,orderId=1001,symbol=BTC-USDT,status=SUBMITTED", "1970-01-20T00:15:00Z");
		insertAudit("ORDER_CREATE_FAIL", "FAIL", "admin", "code=TRADE_DISABLED,message=trade is disabled", "1970-01-20T00:45:00Z");
		insertAudit("ORDER_CREATE_SUCCESS", "SUCCESS", "admin", "accountId=1,orderId=1002,symbol=ETH-USDT,status=SUBMITTED", "1970-01-20T02:10:00Z");
		insertAudit("ORDER_CREATE_FAIL", "FAIL", "other", "code=TRADE_DISABLED,message=trade is disabled", "1970-01-20T02:20:00Z");

		List<AuthAuditLogRepository.OrderCreateTrendRow> rows = authAuditLogRepository.countOrderCreateTrend("admin", from, to, 3600L);

		assertEquals(3, rows.size());
		assertEquals(from.toEpochMilli(), rows.get(0).getBucketStartTs());
		assertEquals("ORDER_CREATE_FAIL", rows.get(0).getAction());
		assertEquals(1L, rows.get(0).getTotal());
		assertEquals(from.toEpochMilli(), rows.get(1).getBucketStartTs());
		assertEquals("ORDER_CREATE_SUCCESS", rows.get(1).getAction());
		assertEquals(1L, rows.get(1).getTotal());
		assertEquals(from.plusSeconds(7200L).toEpochMilli(), rows.get(2).getBucketStartTs());
		assertEquals("ORDER_CREATE_SUCCESS", rows.get(2).getAction());
		assertEquals(1L, rows.get(2).getTotal());
	}

	private long totalByAction(List<AuthAuditLogRepository.OrderCreateActionCountRow> rows, String action) {
		return rows.stream()
			.filter(row -> action.equals(row.getAction()))
			.map(AuthAuditLogRepository.OrderCreateActionCountRow::getTotal)
			.findFirst()
			.orElse(0L);
	}

	private void insertAudit(String action, String result, String username, String reason, String createdAtIso) {
		jdbcTemplate.update(
			"""
				insert into auth_audit_log
				(user_id, username, action, result, reason, trace_id, request_ip, request_path, request_method, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
			1L,
			username,
			action,
			result,
			reason,
			"trace-" + action + "-" + createdAtIso,
			"127.0.0.1",
			"/api/orders",
			"POST",
			Timestamp.from(Instant.parse(createdAtIso))
		);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EntityScan(basePackageClasses = AuthAuditLogEntity.class)
	@EnableJpaRepositories(basePackageClasses = AuthAuditLogRepository.class)
	static class TestApp {
	}

	public static final class SqlFunctions {
		private SqlFunctions() {
		}

		public static String substringIndex(String input, String delimiter, int count) {
			if (input == null || delimiter == null || delimiter.isEmpty() || count == 0) {
				return input;
			}
			String[] parts = input.split(java.util.regex.Pattern.quote(delimiter), -1);
			if (count > 0) {
				int end = Math.min(count, parts.length);
				return String.join(delimiter, java.util.Arrays.copyOfRange(parts, 0, end));
			}
			int start = Math.max(0, parts.length + count);
			return String.join(delimiter, java.util.Arrays.copyOfRange(parts, start, parts.length));
		}
	}
}
