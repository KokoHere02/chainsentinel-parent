package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.rule.model.EventRuleCondition;
import com.chainsentinel.core.rule.model.EventRuleConditionItem;
import com.chainsentinel.core.rule.model.EventRuleField;
import com.chainsentinel.core.rule.model.EventRuleOperator;
import com.chainsentinel.core.rule.model.EventRuleSpec;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AmountRuleExecutionFlowTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Mock
    private AlertEventRepository alertEventRepository;

    @Test
    void shouldCreateAlertWhenAmountRuleMatched() {
        EventRuleConditionParser parser = new EventRuleConditionParser(new ObjectMapper());
        AddressAlertMatcher matcher = new AddressAlertMatcher(
                alertRuleRepository,
                alertEventRepository,
                parser,
                new SimpleMeterRegistry()
        );

        EventRuleSpec spec = new EventRuleSpec(
                1,
                "EVENT",
                new EventRuleCondition(List.of(
                        new EventRuleConditionItem(EventRuleField.CHAIN, EventRuleOperator.EQ, "ETH"),
                        new EventRuleConditionItem(EventRuleField.AMOUNT, EventRuleOperator.GTE, "100")
                ))
        );

        AlertRuleEntity amountRule = new AlertRuleEntity();
        ReflectionTestUtils.setField(amountRule, "id", 9L);
        amountRule.setType(AlertRuleType.AMOUNT);
        amountRule.setSeverity("CRITICAL");
        amountRule.setConditionJson(parser.serialize(spec));

        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 101L);
        event.setChain("ETH");
        event.setAmount("150");

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(amountRule));
        when(alertEventRepository.existsByRuleIdAndAssetEventId(9L, 101L)).thenReturn(false);

        matcher.evaluate(event);

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(alertEventRepository).save(captor.capture());

        AlertEventEntity alert = captor.getValue();
        assertNotNull(alert);
        assertEquals(9L, alert.getRuleId());
        assertEquals(101L, alert.getAssetEventId());
        assertEquals("CRITICAL", alert.getSeverity());
        assertEquals("PENDING", alert.getSendStatus());
    }
}
