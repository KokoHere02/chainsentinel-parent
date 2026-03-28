package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.AlertRuleService;
import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAlertRuleService implements AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final ObjectMapper objectMapper;

    public DefaultAlertRuleService(AlertRuleRepository alertRuleRepository, ObjectMapper objectMapper) {
        this.alertRuleRepository = alertRuleRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AlertRuleView create(AlertRuleCreateCommand command) {
        AlertRuleEntity entity = new AlertRuleEntity();
        entity.setName(command.name());
        entity.setType(command.type());
        entity.setSeverity(command.severity());
        entity.setEnabled(Boolean.TRUE.equals(command.enabled()));
        entity.setConditionJson(toJson(command.condition()));

        AlertRuleEntity saved = alertRuleRepository.save(entity);
        return new AlertRuleView(
                saved.getId(),
                saved.getName(),
                saved.getType(),
                saved.getConditionJson(),
                saved.getSeverity(),
                saved.getEnabled()
        );
    }

    private String toJson(Map<String, Object> condition) {
        Map<String, Object> src = condition == null ? Map.of() : condition;
        try {
            return objectMapper.writeValueAsString(src);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid condition json", e);
        }
    }
}
