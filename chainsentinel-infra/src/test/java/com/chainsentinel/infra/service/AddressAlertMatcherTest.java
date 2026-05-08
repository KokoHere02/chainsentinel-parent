package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AddressAlertMatcherTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Mock
    private AlertEventRepository alertEventRepository;

    @Mock
    private EventRuleConditionParser ruleConditionParser;

    private SimpleMeterRegistry meterRegistry;
    private AddressAlertMatcher matcher;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        matcher = new AddressAlertMatcher(alertRuleRepository, alertEventRepository, ruleConditionParser, meterRegistry);
    }

    @Test
    void shouldIgnoreEventWithoutId() {
        AssetEventEntity event = new AssetEventEntity();
        event.setChain("ETH");
        event.setFromAddress("0x1f6A53F2a8eFd225071A13367E10616DCBd0EC76");

        matcher.evaluate(event);

        verifyNoInteractions(alertRuleRepository, alertEventRepository, ruleConditionParser);
    }

    @Test
    void shouldIgnoreReorgedEvent() {
        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 999L);
        event.setStatus(EventStatus.REORGED);

        matcher.evaluate(event);

        verifyNoInteractions(alertRuleRepository, alertEventRepository, ruleConditionParser);
    }

    @Test
    void shouldSkipWhenRuleNotMatched() {
        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 10L);

        AlertRuleEntity rule = new AlertRuleEntity();
        ReflectionTestUtils.setField(rule, "id", 1L);
        rule.setType(AlertRuleType.EVENT);
        rule.setConditionJson("{}");

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(ruleConditionParser.matches("{}", event)).thenReturn(false);

        matcher.evaluate(event);

        verify(alertEventRepository, never()).save(any(AlertEventEntity.class));
        verify(alertEventRepository, never()).existsByRuleIdAndAssetEventId(1L, 10L);
    }

    @Test
    void shouldNotCreateDuplicateAlertForSameRuleAndAssetEvent() {
        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 66L);

        AlertRuleEntity rule = new AlertRuleEntity();
        ReflectionTestUtils.setField(rule, "id", 7L);
        rule.setType(AlertRuleType.EVENT);
        rule.setSeverity("HIGH");
        rule.setConditionJson("{\"version\":1}");

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(ruleConditionParser.matches(rule.getConditionJson(), event)).thenReturn(true);
        when(alertEventRepository.existsByRuleIdAndAssetEventId(7L, 66L)).thenReturn(true);

        matcher.evaluate(event);

        verify(alertEventRepository, never()).save(any(AlertEventEntity.class));
    }

    @Test
    void shouldCreateAlertsForMatchedAddressAndAmountRules() {
        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 99L);

        AlertRuleEntity duplicatedAddressRule = new AlertRuleEntity();
        ReflectionTestUtils.setField(duplicatedAddressRule, "id", 1L);
        duplicatedAddressRule.setType(AlertRuleType.EVENT);
        duplicatedAddressRule.setSeverity("HIGH");
        duplicatedAddressRule.setConditionJson("{\"version\":1}");

        AlertRuleEntity newAmountRule = new AlertRuleEntity();
        ReflectionTestUtils.setField(newAmountRule, "id", 2L);
        newAmountRule.setType(AlertRuleType.EVENT);
        newAmountRule.setSeverity("LOW");
        newAmountRule.setConditionJson("{\"version\":1}");

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(duplicatedAddressRule, newAmountRule));
        when(ruleConditionParser.matches(duplicatedAddressRule.getConditionJson(), event)).thenReturn(true);
        when(ruleConditionParser.matches(newAmountRule.getConditionJson(), event)).thenReturn(true);
        when(alertEventRepository.existsByRuleIdAndAssetEventId(1L, 99L)).thenReturn(true);
        when(alertEventRepository.existsByRuleIdAndAssetEventId(2L, 99L)).thenReturn(false);

        matcher.evaluate(event);

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(alertEventRepository).save(captor.capture());
        AlertEventEntity saved = captor.getValue();
        assertNotNull(saved);
        assertEquals(2L, saved.getRuleId());
        assertEquals(99L, saved.getAssetEventId());
        assertEquals("LOW", saved.getSeverity());
        assertEquals("PENDING", saved.getSendStatus());
        assertEquals(0, saved.getRetryCount());
    }

    @Test
    void shouldSkipUnsupportedFrequencyRuleType() {
        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 88L);

        AlertRuleEntity frequencyRule = new AlertRuleEntity();
        ReflectionTestUtils.setField(frequencyRule, "id", 3L);
        frequencyRule.setType(null);
        frequencyRule.setConditionJson("{\"version\":1}");

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(frequencyRule));

        matcher.evaluate(event);

        verify(ruleConditionParser, never()).matches(any(String.class), any(AssetEventEntity.class));
        verify(alertEventRepository, never()).save(any(AlertEventEntity.class));
    }

    @Test
    void shouldRecordMetricAndSkipWhenRuleIsInvalid() {
        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 77L);

        AlertRuleEntity badRule = new AlertRuleEntity();
        ReflectionTestUtils.setField(badRule, "id", 3L);
        badRule.setType(AlertRuleType.EVENT);
        badRule.setConditionJson("bad-json");

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(badRule));
        when(ruleConditionParser.matches("bad-json", event)).thenThrow(new IllegalArgumentException("Invalid condition_json"));

        matcher.evaluate(event);

        verify(alertEventRepository, never()).save(any(AlertEventEntity.class));
        double count = meterRegistry
                .get("rule_eval_fail_total")
                .tags("ruleId", "3", "type", "EVENT", "reason", "invalid")
                .counter()
                .count();
        assertEquals(1.0, count);
    }

    @Test
    void shouldRecordMetricAndContinueWhenUnexpectedErrorOccurs() {
        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 55L);

        AlertRuleEntity errorRule = new AlertRuleEntity();
        ReflectionTestUtils.setField(errorRule, "id", 5L);
        errorRule.setType(AlertRuleType.EVENT);
        errorRule.setConditionJson("{\"version\":1}");

        AlertRuleEntity matchedRule = new AlertRuleEntity();
        ReflectionTestUtils.setField(matchedRule, "id", 6L);
        matchedRule.setType(AlertRuleType.EVENT);
        matchedRule.setSeverity("HIGH");
        matchedRule.setConditionJson("{\"version\":2}");

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(errorRule, matchedRule));
        when(ruleConditionParser.matches(errorRule.getConditionJson(), event)).thenThrow(new RuntimeException("boom"));
        when(ruleConditionParser.matches(matchedRule.getConditionJson(), event)).thenReturn(true);
        when(alertEventRepository.existsByRuleIdAndAssetEventId(6L, 55L)).thenReturn(false);

        matcher.evaluate(event);

        verify(alertEventRepository).save(any(AlertEventEntity.class));
        double count = meterRegistry
                .get("rule_eval_fail_total")
                .tags("ruleId", "5", "type", "EVENT", "reason", "error")
                .counter()
                .count();
        assertEquals(1.0, count);
    }
}


