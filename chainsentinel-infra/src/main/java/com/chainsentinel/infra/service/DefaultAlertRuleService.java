package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.AlertRuleService;
import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAlertRuleService implements AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final EventRuleConditionParser ruleConditionParser;

    public DefaultAlertRuleService(AlertRuleRepository alertRuleRepository, EventRuleConditionParser ruleConditionParser) {
        this.alertRuleRepository = alertRuleRepository;
        this.ruleConditionParser = ruleConditionParser;
    }

    @Override
    @Transactional
    public AlertRuleView create(AlertRuleCreateCommand command) {
        AlertRuleEntity entity = new AlertRuleEntity();
        entity.setName(command.name());
        entity.setType(command.type());
        entity.setSeverity(command.severity());
        entity.setEnabled(Boolean.TRUE.equals(command.enabled()));
        entity.setConditionJson(ruleConditionParser.serialize(command.condition()));

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
}
