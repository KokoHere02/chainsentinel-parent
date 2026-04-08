package com.chainsentinel.infra.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.model.TokenType;
import com.chainsentinel.core.rule.model.PriceRuleCondition;
import com.chainsentinel.core.rule.model.PriceRuleOperator;
import com.chainsentinel.core.rule.model.PriceRuleSpec;
import com.chainsentinel.infra.config.AlertProperties;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.entity.AssetPriceSnapshotEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.repository.AssetEventRepository;
import com.chainsentinel.infra.repository.AssetPriceSnapshotRepository;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
import com.chainsentinel.infra.rule.PriceRuleConditionParser;
import com.chainsentinel.infra.rule.RuleConditionJsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookAlertDispatchServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	@Mock
	private AlertEventRepository alertEventRepository;
	@Mock
	private AlertRuleRepository alertRuleRepository;
	@Mock
	private AssetEventRepository assetEventRepository;
	@Mock
	private AssetPriceSnapshotRepository assetPriceSnapshotRepository;
	@Mock
	private RestTemplate restTemplate;
	private AlertProperties alertProperties;
	private SimpleMeterRegistry meterRegistry;
	private WebhookAlertDispatchService service;

	@BeforeEach
	void setUp() {
		alertProperties = new AlertProperties();
		alertProperties.setEnabled(true);
		alertProperties.setWebhookUrl("http://localhost/webhook");
		alertProperties.setRetryMax(3);

		meterRegistry = new SimpleMeterRegistry();
		RuleConditionJsonParser parser = new RuleConditionJsonParser(
			objectMapper,
			new EventRuleConditionParser(objectMapper),
			new PriceRuleConditionParser(objectMapper)
		);
		service = new WebhookAlertDispatchService(
			alertProperties,
			alertEventRepository,
			alertRuleRepository,
			assetEventRepository,
			assetPriceSnapshotRepository,
			parser,
			meterRegistry
		);
		ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
	}

	@Test
	void dispatchPendingShouldReturnZeroWhenDisabled() {
		alertProperties.setEnabled(false);

		int sent = service.dispatchPending();

		assertEquals(0, sent);
		verify(alertEventRepository, never()).findTop100BySendStatusOrderByIdAsc(anyString());
	}

	@Test
	void retryOneShouldReturnFalseWhenAlertNotFound() {
		when(alertEventRepository.findById(1L)).thenReturn(Optional.empty());

		assertFalse(service.retryOne(1L));

		verify(alertEventRepository, never()).save(any(AlertEventEntity.class));
	}

	@Test
	void retryOneShouldReturnTrueWhenAlreadySent() {
		AlertEventEntity alert = new AlertEventEntity();
		ReflectionTestUtils.setField(alert, "id", 1L);
		alert.setSendStatus("SENT");
		alert.setRetryCount(0);
		when(alertEventRepository.findById(1L)).thenReturn(Optional.of(alert));

		assertTrue(service.retryOne(1L));

		verify(alertEventRepository, never()).save(any(AlertEventEntity.class));
	}

	@Test
	void retryOneShouldReturnFalseWhenLocked() throws Exception {
		ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
		ReentrantLock lock = new ReentrantLock();
		locks.put(5L, lock);
		ReflectionTestUtils.setField(service, "retryLocks", locks);

		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Thread holder = new Thread(() -> {
			lock.lock();
			locked.countDown();
			try {
				release.await(2, TimeUnit.SECONDS);
			} catch (InterruptedException ignored) {
			} finally {
				lock.unlock();
			}
		});
		holder.start();
		locked.await(1, TimeUnit.SECONDS);

		assertFalse(service.retryOne(5L));

		verify(alertEventRepository, never()).findById(any());
		release.countDown();
		holder.join(1000);
	}

	@Test
	void retryOneShouldMarkFailedWhenRetryReachedMax() {
		AlertEventEntity alert = new AlertEventEntity();
		ReflectionTestUtils.setField(alert, "id", 2L);
		alert.setSendStatus("PENDING");
		alert.setRetryCount(3);
		when(alertEventRepository.findById(2L)).thenReturn(Optional.of(alert));

		assertFalse(service.retryOne(2L));

		ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
		verify(alertEventRepository).save(captor.capture());
		assertEquals("FAILED", captor.getValue().getSendStatus());
	}

	@Test
	void dispatchPendingShouldMarkSentOnHttp2xx() {
		AlertEventEntity alert = new AlertEventEntity();
		ReflectionTestUtils.setField(alert, "id", 3L);
		alert.setRuleId(5L);
		alert.setAssetEventId(7L);
		alert.setSeverity("HIGH");
		alert.setSendStatus("PENDING");
		alert.setRetryCount(0);

		AlertRuleEntity rule = new AlertRuleEntity();
		ReflectionTestUtils.setField(rule, "id", 5L);
		rule.setName("r1");
		rule.setType(AlertRuleType.ADDRESS);

		AssetEventEntity event = new AssetEventEntity();
		ReflectionTestUtils.setField(event, "id", 7L);
		event.setChain("ETH");
		event.setNetwork("mainnet");
		event.setTxHash("0xhash");
		event.setFromAddress("0xfrom");
		event.setToAddress("0xto");
		event.setAmount("1");
		event.setTokenType(TokenType.ETH);
		event.setOccurredAt(Instant.parse("2026-03-28T10:00:00Z"));

		when(alertEventRepository.findTop100BySendStatusOrderByIdAsc("PENDING")).thenReturn(List.of(alert));
		when(alertRuleRepository.findById(5L)).thenReturn(Optional.of(rule));
		when(assetEventRepository.findById(7L)).thenReturn(Optional.of(event));
		when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
			.thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

		int sent = service.dispatchPending();

		assertEquals(1, sent);
		ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
		verify(alertEventRepository).save(captor.capture());
		AlertEventEntity saved = captor.getValue();
		assertEquals("SENT", saved.getSendStatus());
		assertNull(saved.getLastError());
		assertTrue(saved.getSentAt() != null);

		assertEquals(1.0, meterRegistry.get("alert_send_total").counter().count());
		assertEquals(1.0, meterRegistry.get("alert_send_success_total").counter().count());
		assertEquals(1.0, meterRegistry.get("alert_send_success_rate").gauge().value());
	}

	@Test
	void dispatchPendingShouldContainPricePayloadForPriceRule() throws Exception {
		AlertEventEntity alert = new AlertEventEntity();
		ReflectionTestUtils.setField(alert, "id", 6L);
		alert.setRuleId(9L);
		alert.setAssetEventId(null);
		alert.setSeverity("HIGH");
		alert.setSendStatus("PENDING");
		alert.setRetryCount(0);

		AlertRuleEntity priceRule = new AlertRuleEntity();
		ReflectionTestUtils.setField(priceRule, "id", 9L);
		priceRule.setName("btc-watch");
		priceRule.setType(AlertRuleType.PRICE_THRESHOLD);
		priceRule.setConditionJson(buildPriceRuleJson());

		AssetPriceSnapshotEntity snapshot = new AssetPriceSnapshotEntity();
		snapshot.setInstId("BTC-USDT");
		snapshot.setPrice(new BigDecimal("123456.78"));
		snapshot.setBucketTs(LocalDateTime.of(2026, 4, 3, 12, 0));

		when(alertEventRepository.findTop100BySendStatusOrderByIdAsc("PENDING")).thenReturn(List.of(alert));
		when(alertRuleRepository.findById(9L)).thenReturn(Optional.of(priceRule));
		when(assetPriceSnapshotRepository.findTopByInstIdOrderByBucketTsDesc("BTC-USDT"))
			.thenReturn(Optional.of(snapshot));
		when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
			.thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

		service.dispatchPending();

		ArgumentCaptor<HttpEntity> payloadCaptor = ArgumentCaptor.forClass(HttpEntity.class);
		verify(restTemplate).postForEntity(anyString(), payloadCaptor.capture(), eq(String.class));
		Map<String, Object> body = (Map<String, Object>) payloadCaptor.getValue().getBody();

		assertEquals("PRICE_THRESHOLD", body.get("ruleType"));
		assertEquals("BTC-USDT", body.get("symbol"));
		assertEquals("gte", body.get("op"));
		assertEquals("100000", body.get("threshold"));
		assertEquals("ONCE", body.get("triggerMode"));
		assertEquals(new BigDecimal("123456.78"), body.get("currentPrice"));
	}

	@Test
	void dispatchPendingShouldIncreaseRetryMetricOnFailure() {
		AlertEventEntity alert = new AlertEventEntity();
		ReflectionTestUtils.setField(alert, "id", 4L);
		alert.setRuleId(8L);
		alert.setAssetEventId(9L);
		alert.setSeverity("MEDIUM");
		alert.setSendStatus("PENDING");
		alert.setRetryCount(0);

		when(alertEventRepository.findTop100BySendStatusOrderByIdAsc("PENDING")).thenReturn(List.of(alert));
		when(alertRuleRepository.findById(8L)).thenReturn(Optional.empty());
		when(assetEventRepository.findById(9L)).thenReturn(Optional.empty());
		when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
			.thenReturn(new ResponseEntity<>("bad", HttpStatus.INTERNAL_SERVER_ERROR));

		int sent = service.dispatchPending();

		assertEquals(0, sent);
		assertEquals(1.0, meterRegistry.get("alert_retry_total").counter().count());
	}

	private String buildPriceRuleJson() throws Exception {
		PriceRuleCondition condition = new PriceRuleCondition();
		condition.setSymbol("BTC-USDT");
		condition.setOp(PriceRuleOperator.GTE);
		condition.setThreshold("100000");

		PriceRuleSpec spec = new PriceRuleSpec();
		spec.setVersion(1);
		spec.setType("PRICE");
		spec.setCondition(condition);
		return objectMapper.writeValueAsString(spec);
	}
}
