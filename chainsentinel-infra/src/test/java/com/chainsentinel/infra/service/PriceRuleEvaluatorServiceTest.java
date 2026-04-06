package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.rule.model.PriceRuleCondition;
import com.chainsentinel.core.rule.model.PriceRuleOperator;
import com.chainsentinel.core.rule.model.PriceRuleSpec;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.entity.AssetPriceSnapshotEntity;
import com.chainsentinel.infra.entity.RuleTriggerStateEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.repository.AssetPriceSnapshotRepository;
import com.chainsentinel.infra.repository.RuleTriggerStateRepository;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
import com.chainsentinel.infra.rule.PriceRuleConditionParser;
import com.chainsentinel.infra.rule.RuleConditionJsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PriceRuleEvaluatorServiceTest {

  @Mock
  private AlertRuleRepository alertRuleRepository;

  @Mock
  private AlertEventRepository alertEventRepository;

  @Mock
  private AssetPriceSnapshotRepository assetPriceSnapshotRepository;

  @Mock
  private RuleTriggerStateRepository ruleTriggerStateRepository;

  private PriceRuleEvaluatorService service;
  private SimpleMeterRegistry meterRegistry;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    RuleConditionJsonParser parser = new RuleConditionJsonParser(
      objectMapper,
      new EventRuleConditionParser(objectMapper),
      new PriceRuleConditionParser(objectMapper)
    );
    service = new PriceRuleEvaluatorService(
      alertRuleRepository,
      alertEventRepository,
      assetPriceSnapshotRepository,
      ruleTriggerStateRepository,
      parser,
      meterRegistry
    );
  }

  @Test
  void shouldTriggerOnlyOnEdgeAndResetThenTriggerAgain() throws Exception {
    AlertRuleEntity rule = new AlertRuleEntity();
    ReflectionTestUtils.setField(rule, "id", 1L);
    rule.setType(AlertRuleType.PRICE_THRESHOLD);
    rule.setSeverity("HIGH");
    rule.setConditionJson(buildPriceRuleJson("BTC-USDT", "100"));

    when(alertRuleRepository.findByTypeAndEnabledTrue(AlertRuleType.PRICE_THRESHOLD)).thenReturn(List.of(rule));

    AssetPriceSnapshotEntity snapshot = new AssetPriceSnapshotEntity();
    snapshot.setInstId("BTC-USDT");
    snapshot.setPrice(new BigDecimal("110"));
    when(assetPriceSnapshotRepository.findTopByInstIdOrderByBucketTsDesc("BTC-USDT"))
      .thenReturn(Optional.of(snapshot));

    AtomicReference<RuleTriggerStateEntity> stateRef = new AtomicReference<>();
    when(ruleTriggerStateRepository.findByRuleIdAndTargetKey(1L, "BTC-USDT"))
      .thenAnswer(invocation -> Optional.ofNullable(stateRef.get()));
    when(ruleTriggerStateRepository.save(any(RuleTriggerStateEntity.class)))
      .thenAnswer(invocation -> {
        RuleTriggerStateEntity state = invocation.getArgument(0);
        if (state.getId() == null) {
          ReflectionTestUtils.setField(state, "id", 10L);
        }
        stateRef.set(state);
        return state;
      });

    service.evaluateOnce();
    service.evaluateOnce();

    verify(alertEventRepository, times(1)).save(any(AlertEventEntity.class));
    assertEquals(true, stateRef.get().getActive());

    snapshot.setPrice(new BigDecimal("90"));
    service.evaluateOnce();
    assertEquals(false, stateRef.get().getActive());

    snapshot.setPrice(new BigDecimal("120"));
    service.evaluateOnce();
    verify(alertEventRepository, times(2)).save(any(AlertEventEntity.class));

    ArgumentCaptor<AlertEventEntity> alertCaptor = ArgumentCaptor.forClass(AlertEventEntity.class);
    verify(alertEventRepository, times(2)).save(alertCaptor.capture());
    AlertEventEntity latest = alertCaptor.getAllValues().get(1);
    assertEquals(1L, latest.getRuleId());
    assertEquals(null, latest.getAssetEventId());
    assertEquals("PENDING", latest.getSendStatus());
  }

  @Test
  void shouldSkipRetriggerWithinCooldownWindow() throws Exception {
    AlertRuleEntity rule = new AlertRuleEntity();
    ReflectionTestUtils.setField(rule, "id", 2L);
    rule.setType(AlertRuleType.PRICE_THRESHOLD);
    rule.setSeverity("HIGH");
    rule.setConditionJson(buildPriceRuleJson("ETH-USDT", "100", 300));

    when(alertRuleRepository.findByTypeAndEnabledTrue(AlertRuleType.PRICE_THRESHOLD)).thenReturn(List.of(rule));

    AssetPriceSnapshotEntity snapshot = new AssetPriceSnapshotEntity();
    snapshot.setInstId("ETH-USDT");
    snapshot.setPrice(new BigDecimal("110"));
    when(assetPriceSnapshotRepository.findTopByInstIdOrderByBucketTsDesc("ETH-USDT"))
      .thenReturn(Optional.of(snapshot));

    AtomicReference<RuleTriggerStateEntity> stateRef = new AtomicReference<>();
    when(ruleTriggerStateRepository.findByRuleIdAndTargetKey(2L, "ETH-USDT"))
      .thenAnswer(invocation -> Optional.ofNullable(stateRef.get()));
    when(ruleTriggerStateRepository.save(any(RuleTriggerStateEntity.class)))
      .thenAnswer(invocation -> {
        RuleTriggerStateEntity state = invocation.getArgument(0);
        if (state.getId() == null) {
          ReflectionTestUtils.setField(state, "id", 20L);
        }
        stateRef.set(state);
        return state;
      });

    service.evaluateOnce();
    snapshot.setPrice(new BigDecimal("90"));
    service.evaluateOnce();
    snapshot.setPrice(new BigDecimal("120"));
    service.evaluateOnce();

    verify(alertEventRepository, times(1)).save(any(AlertEventEntity.class));
    assertEquals(1.0,
      meterRegistry.get("rule_cooldown_block_total")
        .tag("ruleId", "2")
        .tag("type", "PRICE_THRESHOLD")
        .counter()
        .count());
  }

  @Test
  void shouldRetriggerAfterCooldownWindowPassed() throws Exception {
    AlertRuleEntity rule = new AlertRuleEntity();
    ReflectionTestUtils.setField(rule, "id", 3L);
    rule.setType(AlertRuleType.PRICE_THRESHOLD);
    rule.setSeverity("HIGH");
    rule.setConditionJson(buildPriceRuleJson("SOL-USDT", "100", 60));

    when(alertRuleRepository.findByTypeAndEnabledTrue(AlertRuleType.PRICE_THRESHOLD)).thenReturn(List.of(rule));

    AssetPriceSnapshotEntity snapshot = new AssetPriceSnapshotEntity();
    snapshot.setInstId("SOL-USDT");
    snapshot.setPrice(new BigDecimal("120"));
    when(assetPriceSnapshotRepository.findTopByInstIdOrderByBucketTsDesc("SOL-USDT"))
      .thenReturn(Optional.of(snapshot));

    RuleTriggerStateEntity existing = new RuleTriggerStateEntity();
    ReflectionTestUtils.setField(existing, "id", 30L);
    existing.setRuleId(3L);
    existing.setTargetKey("SOL-USDT");
    existing.setActive(false);
    existing.setLastTriggeredAt(Instant.now().minusSeconds(120));
    existing.setLastValue(new BigDecimal("90"));

    when(ruleTriggerStateRepository.findByRuleIdAndTargetKey(3L, "SOL-USDT"))
      .thenReturn(Optional.of(existing));
    when(ruleTriggerStateRepository.save(any(RuleTriggerStateEntity.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    service.evaluateOnce();

    verify(alertEventRepository, times(1)).save(any(AlertEventEntity.class));
  }

  @Test
  void shouldRunFullFlowCreateRuleTriggerCooldownBlockAndRetriggerAfterWindow() throws Exception {
    AlertRuleEntity rule = new AlertRuleEntity();
    ReflectionTestUtils.setField(rule, "id", 11L);
    rule.setType(AlertRuleType.PRICE_THRESHOLD);
    rule.setSeverity("HIGH");
    rule.setConditionJson(buildPriceRuleJson("BTC-USDT", "100", 60));

    when(alertRuleRepository.findByTypeAndEnabledTrue(AlertRuleType.PRICE_THRESHOLD)).thenReturn(List.of(rule));

    AssetPriceSnapshotEntity snapshot = new AssetPriceSnapshotEntity();
    snapshot.setInstId("BTC-USDT");
    snapshot.setPrice(new BigDecimal("120"));
    when(assetPriceSnapshotRepository.findTopByInstIdOrderByBucketTsDesc("BTC-USDT"))
      .thenReturn(Optional.of(snapshot));

    AtomicReference<RuleTriggerStateEntity> stateRef = new AtomicReference<>();
    when(ruleTriggerStateRepository.findByRuleIdAndTargetKey(11L, "BTC-USDT"))
      .thenAnswer(invocation -> Optional.ofNullable(stateRef.get()));
    when(ruleTriggerStateRepository.save(any(RuleTriggerStateEntity.class)))
      .thenAnswer(invocation -> {
        RuleTriggerStateEntity state = invocation.getArgument(0);
        if (state.getId() == null) {
          ReflectionTestUtils.setField(state, "id", 110L);
        }
        stateRef.set(state);
        return state;
      });

    // 1) First hit after rule creation should create an alert.
    service.evaluateOnce();
    verify(alertEventRepository, times(1)).save(any(AlertEventEntity.class));
    assertEquals(true, stateRef.get().getActive());
    Instant firstTriggeredAt = stateRef.get().getLastTriggeredAt();

    // 2) Price drops below threshold, state resets.
    snapshot.setPrice(new BigDecimal("90"));
    service.evaluateOnce();
    assertEquals(false, stateRef.get().getActive());

    // 3) Hit again inside cooldown window, should be blocked.
    snapshot.setPrice(new BigDecimal("130"));
    service.evaluateOnce();
    verify(alertEventRepository, times(1)).save(any(AlertEventEntity.class));
    assertEquals(false, stateRef.get().getActive());
    assertEquals(firstTriggeredAt, stateRef.get().getLastTriggeredAt());

    // 4) Move lastTriggeredAt outside cooldown window, should trigger again.
    stateRef.get().setLastTriggeredAt(Instant.now().minusSeconds(120));
    snapshot.setPrice(new BigDecimal("140"));
    service.evaluateOnce();
    verify(alertEventRepository, times(2)).save(any(AlertEventEntity.class));
    assertEquals(true, stateRef.get().getActive());
  }

  private String buildPriceRuleJson(String symbol, String threshold) throws Exception {
    return buildPriceRuleJson(symbol, threshold, null);
  }

  private String buildPriceRuleJson(String symbol, String threshold, Integer cooldownSec) throws Exception {
    PriceRuleCondition condition = new PriceRuleCondition();
    condition.setSymbol(symbol);
    condition.setOp(PriceRuleOperator.GTE);
    condition.setThreshold(threshold);
    condition.setCooldownSec(cooldownSec);

    PriceRuleSpec spec = new PriceRuleSpec();
    spec.setVersion(1);
    spec.setType("PRICE");
    spec.setCondition(condition);
    return objectMapper.writeValueAsString(spec);
  }
}
