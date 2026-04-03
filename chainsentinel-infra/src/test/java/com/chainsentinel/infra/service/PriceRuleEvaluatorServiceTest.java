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

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
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
      new SimpleMeterRegistry()
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

  private String buildPriceRuleJson(String symbol, String threshold) throws Exception {
    PriceRuleCondition condition = new PriceRuleCondition();
    condition.setSymbol(symbol);
    condition.setOp(PriceRuleOperator.GTE);
    condition.setThreshold(threshold);

    PriceRuleSpec spec = new PriceRuleSpec();
    spec.setVersion(1);
    spec.setType("PRICE");
    spec.setCondition(condition);
    return objectMapper.writeValueAsString(spec);
  }
}
