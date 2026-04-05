package com.chainsentinel.infra.service;

import com.chainsentinel.core.exception.RuleGovernanceException;
import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.service.AlertRuleService;
import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.rule.RuleConditionJsonParser;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAlertRuleService implements AlertRuleService {

private static final Logger log = LoggerFactory.getLogger(DefaultAlertRuleService.class);
private static final Set<AlertRuleType> ENABLED_RULE_TYPES = EnumSet.of(
AlertRuleType.ADDRESS,
AlertRuleType.AMOUNT,
AlertRuleType.PRICE_THRESHOLD
);

private final AlertRuleRepository alertRuleRepository;
private final RuleConditionJsonParser ruleConditionJsonParser;

public DefaultAlertRuleService(
AlertRuleRepository alertRuleRepository,
RuleConditionJsonParser ruleConditionJsonParser
) {
this.alertRuleRepository = alertRuleRepository;
this.ruleConditionJsonParser = ruleConditionJsonParser;
}

@Override
@Transactional
public AlertRuleView create(AlertRuleCreateCommand command) {
validateRuleType(command.type());

AlertRuleEntity entity = new AlertRuleEntity();
entity.setName(command.name());
entity.setType(command.type());
entity.setSeverity(command.severity());
entity.setEnabled(Boolean.TRUE.equals(command.enabled()));
entity.setConditionJson(ruleConditionJsonParser.serialize(command.type(), command.condition()));

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

private void validateRuleType(AlertRuleType type) {
if (type != null && ENABLED_RULE_TYPES.contains(type)) {
return;
}
log.warn("rule.governance.reject type={} enabledTypes={}", type, ENABLED_RULE_TYPES);
throw new RuleGovernanceException(type);
}
}
