package com.chainsentinel.web.api;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.common.crypto.AesGcmCryptoUtil;
import com.chainsentinel.core.service.dto.TradeOrderCreateCommand;
import com.chainsentinel.infra.entity.TradeAccountEntity;
import com.chainsentinel.infra.repository.AuthAuditLogRepository;
import com.chainsentinel.infra.repository.TradeAccountRepository;
import com.chainsentinel.infra.repository.TradeOrderRepository;
import com.chainsentinel.infra.service.TradeOrderProvider;
import com.chainsentinel.infra.service.TradeProviderCancelResult;
import com.chainsentinel.infra.service.TradeProviderFillState;
import com.chainsentinel.infra.service.TradeProviderOrderState;
import com.chainsentinel.infra.service.TradeProviderSubmitResult;
import com.chainsentinel.web.ChainSentinelApplication;
import com.chainsentinel.web.auth.AuthPrincipal;
import com.chainsentinel.web.auth.AuthRole;
import com.chainsentinel.web.auth.JwtTokenService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
	classes = ChainSentinelApplication.class,
	properties = {
		"spring.datasource.url=jdbc:h2:mem:trade-success-audit-it;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.open-in-view=false",
		"spring.flyway.enabled=false",
		"spring.main.allow-bean-definition-overriding=true",
		"chainsentinel.auth.jwt-secret=test-jwt-secret-for-success-integration",
		"chainsentinel.auth.bootstrap-admin-enabled=false",
		"chainsentinel.trade.enabled=true",
		"chainsentinel.trade.sandbox-only=true",
		"chainsentinel.scanner.enabled=false",
		"chainsentinel.alert.enabled=false",
		"chainsentinel.price.tick-retention.enabled=false"
	}
)
@AutoConfigureMockMvc
@Import(TradeOrderSuccessAuditIntegrationTest.TestConfig.class)
class TradeOrderSuccessAuditIntegrationTest {

	private static final String PROVIDER = "STUBX";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@Autowired
	private TradeAccountRepository tradeAccountRepository;

	@Autowired
	private TradeOrderRepository tradeOrderRepository;

	@Autowired
	private AuthAuditLogRepository authAuditLogRepository;

	@Autowired
	private AesGcmCryptoUtil aesGcmCryptoUtil;

	private String adminToken;
	private Long accountId;

	private static final String TEST_CRYPTO_MATERIAL_B64 = "AAAAAAAAAAAAAAAAAAAAAA==";

	@BeforeEach
	void setUp() {
		authAuditLogRepository.deleteAll();
		tradeOrderRepository.deleteAll();
		tradeAccountRepository.deleteAll();
		adminToken = jwtTokenService.issueToken(new AuthPrincipal(1L, "admin", Set.of(AuthRole.ADMIN)));
		accountId = createStubAccount();
	}

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("chainsentinel.security.crypto.key-base64", () -> TEST_CRYPTO_MATERIAL_B64);
	}

	@Test
	void shouldCreateOrderAndPersistSuccessAudit() throws Exception {
		mockMvc.perform(post("/api/orders")
				.header("Authorization", "Bearer " + adminToken)
				.contentType("application/json")
				.content("""
					{
					  "accountId": %d,
					  "symbol": "BTC-USDT",
					  "side": "BUY",
					  "orderType": "MARKET",
					  "quantity": 0.01,
					  "clientOrderId": "it-success-1"
					}
					""".formatted(accountId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accountId", is(accountId.intValue())))
			.andExpect(jsonPath("$.provider", is(PROVIDER)))
			.andExpect(jsonPath("$.symbol", is("BTC-USDT")))
			.andExpect(jsonPath("$.status", is("SUBMITTED")))
			.andExpect(jsonPath("$.providerOrderId", is("stub-order-1")))
			.andExpect(jsonPath("$.createdBy", is(1)));

		await()
			.atMost(Duration.ofSeconds(3))
			.untilAsserted(() -> org.junit.jupiter.api.Assertions.assertEquals(
				1L,
				authAuditLogRepository.findAll().stream()
					.filter(row -> "ORDER_CREATE_SUCCESS".equals(row.getAction()))
					.filter(row -> "/api/orders".equals(row.getRequestPath()))
					.count()
			));

		org.junit.jupiter.api.Assertions.assertEquals(1L, tradeOrderRepository.count());
		org.junit.jupiter.api.Assertions.assertEquals(
			1L,
			tradeOrderRepository.findAll().stream()
				.filter(order -> "SUBMITTED".equals(order.getStatus()))
				.filter(order -> PROVIDER.equals(order.getProvider()))
				.count()
		);

		mockMvc.perform(get("/api/admin/trade-audit/order-create")
				.header("Authorization", "Bearer " + adminToken)
				.param("result", "SUCCESS"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].action", is("ORDER_CREATE_SUCCESS")))
			.andExpect(jsonPath("$[0].result", is("SUCCESS")))
			.andExpect(jsonPath("$[0].userId", is(1)))
			.andExpect(jsonPath("$[0].username", is("admin")))
			.andExpect(jsonPath("$[0].accountId", is(accountId.intValue())))
			.andExpect(jsonPath("$[0].symbol", is("BTC-USDT")))
			.andExpect(jsonPath("$[0].orderStatus", is("SUBMITTED")))
			.andExpect(jsonPath("$[0].reason", is("accountId=" + accountId + ",orderId=1,symbol=BTC-USDT,status=SUBMITTED")));
	}

	private Long createStubAccount() {
		TradeAccountEntity account = new TradeAccountEntity();
		account.setName("stub-main");
		account.setProvider(PROVIDER);
		account.setAccountType("API_KEY");
		account.setEnvType("SIMULATED");
		account.setApiKey("stub-key");
		account.setApiSecretCipher(aesGcmCryptoUtil.encrypt("stub-secret"));
		account.setPassphraseCipher(aesGcmCryptoUtil.encrypt("stub-passphrase"));
		account.setEnabled(true);
		account.setCreatedBy(1L);
		account.setUpdatedBy(1L);
		return tradeAccountRepository.save(account).getId();
	}

	@TestConfiguration
	static class TestConfig {

		@Bean(name = "auditEventExecutor")
		@Primary
		Executor auditEventExecutor() {
			return Runnable::run;
		}

		@Bean
		TradeOrderProvider stubTradeOrderProvider() {
			return new TradeOrderProvider() {
				@Override
				public String provider() {
					return PROVIDER;
				}

				@Override
				public TradeProviderSubmitResult submit(
					TradeAccountEntity account,
					String apiSecret,
					String passphrase,
					TradeOrderCreateCommand command
				) {
					return new TradeProviderSubmitResult(true, "stub-order-1", "SUBMITTED", null, null);
				}

				@Override
				public TradeProviderCancelResult cancel(TradeAccountEntity account, String apiSecret, String passphrase, com.chainsentinel.infra.entity.TradeOrderEntity order) {
					return new TradeProviderCancelResult(true, "CANCELED", null, null);
				}

				@Override
				public TradeProviderOrderState queryOrder(TradeAccountEntity account, String apiSecret, String passphrase, com.chainsentinel.infra.entity.TradeOrderEntity order) {
					return new TradeProviderOrderState(true, "SUBMITTED", "stub-order-1", null, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
				}

				@Override
				public List<TradeProviderFillState> listFills(TradeAccountEntity account, String apiSecret, String passphrase, com.chainsentinel.infra.entity.TradeOrderEntity order) {
					return List.of();
				}
			};
		}
	}
}
