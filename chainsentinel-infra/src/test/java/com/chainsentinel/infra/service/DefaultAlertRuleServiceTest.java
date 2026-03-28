package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultAlertRuleServiceTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Test
    void shouldCreateRuleAndSerializeCondition() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        DefaultAlertRuleService service = new DefaultAlertRuleService(alertRuleRepository, objectMapper);

        when(alertRuleRepository.save(any(AlertRuleEntity.class))).thenAnswer(invocation -> {
            AlertRuleEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 7L);
            return entity;
        });

        AlertRuleCreateCommand command = new AlertRuleCreateCommand(
                "address-watch",
                AlertRuleType.ADDRESS,
                Map.of("threshold", 100),
                "HIGH",
                null
        );

        AlertRuleView view = service.create(command);

        assertEquals(7L, view.id());
        assertEquals("address-watch", view.name());
        assertEquals(AlertRuleType.ADDRESS, view.type());
        assertEquals("HIGH", view.severity());
        assertFalse(view.enabled());
        assertEquals(100, objectMapper.readTree(view.conditionJson()).get("threshold").asInt());
    }

    @Test
    void shouldUseEmptyJsonWhenConditionIsNull() {
        ObjectMapper objectMapper = new ObjectMapper();
        DefaultAlertRuleService service = new DefaultAlertRuleService(alertRuleRepository, objectMapper);

        when(alertRuleRepository.save(any(AlertRuleEntity.class))).thenAnswer(invocation -> {
            AlertRuleEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 8L);
            return entity;
        });

        AlertRuleCreateCommand command = new AlertRuleCreateCommand(
                "rule-2",
                AlertRuleType.ADDRESS,
                null,
                "LOW",
                true
        );

        AlertRuleView view = service.create(command);
        assertEquals("{}", view.conditionJson());
        assertEquals(true, view.enabled());
    }

    @Test
    void shouldThrowIllegalArgumentWhenJsonSerializationFails() throws Exception {
        ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });

        DefaultAlertRuleService service = new DefaultAlertRuleService(alertRuleRepository, objectMapper);

        AlertRuleCreateCommand command = new AlertRuleCreateCommand(
                "bad-rule",
                AlertRuleType.ADDRESS,
                Map.of("k", "v"),
                "HIGH",
                true
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(command));
        assertEquals("Invalid condition json", ex.getMessage());
    }
}
