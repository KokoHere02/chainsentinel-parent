package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.awaitility.Awaitility.await;

import com.chainsentinel.infra.repository.AuthAuditLogRepository;
import com.chainsentinel.web.ChainSentinelApplication;
import com.chainsentinel.web.auth.AuthPrincipal;
import com.chainsentinel.web.auth.AuthRole;
import com.chainsentinel.web.auth.JwtTokenService;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
	classes = ChainSentinelApplication.class,
	properties = {
		"spring.datasource.url=jdbc:h2:mem:trade-audit-it;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.open-in-view=false",
		"spring.flyway.enabled=false",
		"spring.task.scheduling.enabled=false",
		"spring.main.allow-bean-definition-overriding=true",
		"chainsentinel.auth.jwt-secret=test-jwt-secret-for-integration",
		"chainsentinel.auth.bootstrap-admin-enabled=false",
		"chainsentinel.trade.enabled=false",
		"chainsentinel.trade.sandbox-only=true",
		"chainsentinel.scanner.enabled=false",
		"chainsentinel.alert.enabled=false",
		"chainsentinel.price.tick-retention.enabled=false"
	}
)
@AutoConfigureMockMvc
class TradeOrderAuditIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@Autowired
	private AuthAuditLogRepository authAuditLogRepository;

	private String adminToken;

	private static final String TEST_CRYPTO_MATERIAL_B64 = "AAAAAAAAAAAAAAAAAAAAAA==";

	@BeforeEach
	void setUp() {
		adminToken = jwtTokenService.issueToken(new AuthPrincipal(1L, "admin", Set.of(AuthRole.ADMIN)));
	}

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("chainsentinel.security.crypto.key-base64", () -> TEST_CRYPTO_MATERIAL_B64);
	}

	@Test
	void shouldPersistRejectAuditAndQueryItThroughAdminApi() throws Exception {
		mockMvc.perform(post("/api/orders")
				.header("Authorization", "Bearer " + adminToken)
				.contentType("application/json")
				.content("""
					{
					  "accountId": 1,
					  "symbol": "BTC-USDT",
					  "side": "BUY",
					  "orderType": "MARKET",
					  "quantity": 0.01
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(header().exists("X-Request-Id"))
			.andExpect(jsonPath("$.code", is("TRADE_DISABLED")))
			.andExpect(jsonPath("$.path", is("/api/orders")))
			.andExpect(jsonPath("$.traceId", notNullValue()));

		await()
			.atMost(Duration.ofSeconds(3))
			.untilAsserted(() -> org.junit.jupiter.api.Assertions.assertEquals(
				1L,
				authAuditLogRepository.findAll().stream()
					.filter(row -> "ORDER_CREATE_FAIL".equals(row.getAction()))
					.filter(row -> "/api/orders".equals(row.getRequestPath()))
					.count()
			));

		mockMvc.perform(get("/api/admin/trade-audit/order-create")
				.header("Authorization", "Bearer " + adminToken)
				.param("result", "FAIL"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].action", is("ORDER_CREATE_FAIL")))
			.andExpect(jsonPath("$[0].result", is("FAIL")))
			.andExpect(jsonPath("$[0].userId", is(1)))
			.andExpect(jsonPath("$[0].username", is("admin")))
			.andExpect(jsonPath("$[0].rejectCode", is("TRADE_DISABLED")))
			.andExpect(jsonPath("$[0].reason", is("code=TRADE_DISABLED,message=trade is disabled")))
			.andExpect(jsonPath("$[0].traceId", notNullValue()))
			.andExpect(jsonPath("$[0].requestIp", notNullValue()));
	}

	@TestConfiguration
	static class TestConfig {

		@Bean(name = "auditEventExecutor")
		Executor auditEventExecutor() {
			return Runnable::run;
		}
	}
}
