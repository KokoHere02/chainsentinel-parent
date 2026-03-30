package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.rule.model.EventRuleCondition;
import com.chainsentinel.core.rule.model.EventRuleConditionItem;
import com.chainsentinel.core.rule.model.EventRuleField;
import com.chainsentinel.core.rule.model.EventRuleOperator;
import com.chainsentinel.core.rule.model.EventRuleSpec;
import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultAlertRuleServiceTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    private final EventRuleConditionParser parser = new EventRuleConditionParser(new ObjectMapper());

    @Test
    void shouldCreateRuleAndSerializeConditionObject() throws Exception {
        DefaultAlertRuleService service = new DefaultAlertRuleService(alertRuleRepository, parser);

        when(alertRuleRepository.save(any(AlertRuleEntity.class))).thenAnswer(invocation -> {
            AlertRuleEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 7L);
            return entity;
        });

        EventRuleSpec spec = new EventRuleSpec(
                1,
                "EVENT",
                new EventRuleCondition(List.of(
                        new EventRuleConditionItem(EventRuleField.CHAIN, EventRuleOperator.EQ, "ETH"),
                        new EventRuleConditionItem(EventRuleField.NETWORK, EventRuleOperator.EQ, "sepolia"),
                        new EventRuleConditionItem(EventRuleField.AMOUNT, EventRuleOperator.GTE, "100")
                ))
        );

        AlertRuleCreateCommand command = new AlertRuleCreateCommand(
                "address-watch",
                AlertRuleType.ADDRESS,
                spec,
                "HIGH",
                true
        );

        AlertRuleView view = service.create(command);

//        assertEquals(7L, view.id());
        assertEquals("address-watch", view.name());
        assertEquals(AlertRuleType.ADDRESS, view.type());
        assertEquals("HIGH", view.severity());
        assertTrue(view.enabled());
        assertEquals(1, new ObjectMapper().readTree(view.conditionJson()).get("version").asInt());
    }

    @Test
    void shouldThrowIllegalArgumentWhenConditionIsInvalid() {
        DefaultAlertRuleService service = new DefaultAlertRuleService(alertRuleRepository, parser);

        EventRuleSpec invalid = new EventRuleSpec();
        invalid.setVersion(1);
        invalid.setType("EVENT");

        AlertRuleCreateCommand command = new AlertRuleCreateCommand(
                "bad-rule",
                AlertRuleType.ADDRESS,
                invalid,
                "HIGH",
                true
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(command));
        assertEquals("condition.all must be a non-empty array", ex.getMessage());
    }
}
