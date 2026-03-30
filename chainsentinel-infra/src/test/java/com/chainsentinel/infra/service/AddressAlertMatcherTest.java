package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
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

    private AddressAlertMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new AddressAlertMatcher(alertRuleRepository, alertEventRepository, ruleConditionParser);
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
    void shouldSkipWhenRuleNotMatched() {
        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 10L);

        AlertRuleEntity rule = new AlertRuleEntity();
        ReflectionTestUtils.setField(rule, "id", 1L);
        rule.setConditionJson("{}");

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(ruleConditionParser.matches("{}", event)).thenReturn(false);

        matcher.evaluate(event);

        verify(alertEventRepository, never()).save(org.mockito.ArgumentMatchers.any(AlertEventEntity.class));
        verify(alertEventRepository, never()).existsByRuleIdAndAssetEventId(1L, 10L);
    }

    @Test
    void shouldCreateAlertsForMatchedUnsentRules() {
        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 99L);

        AlertRuleEntity duplicated = new AlertRuleEntity();
        ReflectionTestUtils.setField(duplicated, "id", 1L);
        duplicated.setSeverity("HIGH");
        duplicated.setConditionJson("{\"version\":1}");

        AlertRuleEntity newRule = new AlertRuleEntity();
        ReflectionTestUtils.setField(newRule, "id", 2L);
        newRule.setSeverity("LOW");
        newRule.setConditionJson("{\"version\":1}");

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(duplicated, newRule));
        when(ruleConditionParser.matches(duplicated.getConditionJson(), event)).thenReturn(true);
        when(ruleConditionParser.matches(newRule.getConditionJson(), event)).thenReturn(true);
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
    void shouldSkipInvalidRuleJson() {
        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 77L);

        AlertRuleEntity badRule = new AlertRuleEntity();
        ReflectionTestUtils.setField(badRule, "id", 3L);
        badRule.setConditionJson("bad-json");

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(badRule));
        when(ruleConditionParser.matches("bad-json", event)).thenThrow(new IllegalArgumentException("Invalid condition_json"));

        matcher.evaluate(event);

        verify(alertEventRepository, never()).save(org.mockito.ArgumentMatchers.any(AlertEventEntity.class));
    }
}
