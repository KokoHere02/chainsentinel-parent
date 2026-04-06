package com.chainsentinel.infra.service;

import com.chainsentinel.core.exception.NotFoundException;
import com.chainsentinel.core.exception.RuleGovernanceException;
import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.service.AlertRuleService;
import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRulePatchConditionCommand;
import com.chainsentinel.core.service.dto.AlertRuleQueryCommand;
import com.chainsentinel.core.service.dto.AlertRuleUpdateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.rule.RuleConditionJsonParser;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.StringUtils;
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

@Override
@Transactional
public AlertRuleView update(AlertRuleUpdateCommand command) {
Long id = Objects.requireNonNull(command.id(), "id is required");
AlertRuleEntity entity = alertRuleRepository.findById(id)
	.orElseThrow(() -> new NotFoundException("Rule not found: " + id));

	entity.setName(command.name());
	entity.setSeverity(command.severity());
	entity.setEnabled(Boolean.TRUE.equals(command.enabled()));
	entity.setConditionJson(ruleConditionJsonParser.serialize(entity.getType(), command.condition()));

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

@Override
@Transactional
public AlertRuleView patchCondition(AlertRulePatchConditionCommand command) {
Long id = Objects.requireNonNull(command.id(), "id is required");
AlertRuleEntity entity = alertRuleRepository.findById(id)
	.orElseThrow(() -> new NotFoundException("Rule not found: " + id));

	entity.setConditionJson(ruleConditionJsonParser.serialize(entity.getType(), command.condition()));

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

@Override
@Transactional(readOnly = true)
public List<AlertRuleView> list(AlertRuleQueryCommand command) {
	String keyword = command == null ? null : command.keyword();
	AlertRuleType type = command == null ? null : command.type();
	Boolean enabled = command == null ? null : command.enabled();
	String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;

	return alertRuleRepository.findAll().stream()
		.filter(rule -> type == null || type == rule.getType())
		.filter(rule -> enabled == null || enabled.equals(rule.getEnabled()))
		.filter(rule -> {
			if (normalizedKeyword == null) {
				return true;
			}
			return StringUtils.hasText(rule.getName()) && rule.getName().toLowerCase().contains(normalizedKeyword);
		})
		.sorted((a, b) -> {
			Long aId = a.getId() == null ? Long.MIN_VALUE : a.getId();
			Long bId = b.getId() == null ? Long.MIN_VALUE : b.getId();
			return bId.compareTo(aId);
		})
		.map(rule -> new AlertRuleView(
			rule.getId(),
			rule.getName(),
			rule.getType(),
			rule.getConditionJson(),
			rule.getSeverity(),
			rule.getEnabled()
		))
		.toList();
}

@Override
@Transactional
public AlertRuleView delete(Long id) {
	Long ruleId = Objects.requireNonNull(id, "id is required");
	AlertRuleEntity entity = alertRuleRepository.findById(ruleId)
		.orElseThrow(() -> new NotFoundException("Rule not found: " + ruleId));

	if (Boolean.TRUE.equals(entity.getEnabled())) {
		entity.setEnabled(false);
		entity = alertRuleRepository.save(entity);
	}

	return new AlertRuleView(
		entity.getId(),
		entity.getName(),
		entity.getType(),
		entity.getConditionJson(),
		entity.getSeverity(),
		entity.getEnabled()
	);
}

@Override
@Transactional(readOnly = true)
public AlertRuleView getById(Long id) {
	Long ruleId = Objects.requireNonNull(id, "id is required");
	AlertRuleEntity entity = alertRuleRepository.findById(ruleId)
		.orElseThrow(() -> new NotFoundException("Rule not found: " + ruleId));

	return new AlertRuleView(
		entity.getId(),
		entity.getName(),
		entity.getType(),
		entity.getConditionJson(),
		entity.getSeverity(),
		entity.getEnabled()
	);
}

@Override
@Transactional
public AlertRuleView setEnabled(Long id, boolean enabled) {
	Long ruleId = Objects.requireNonNull(id, "id is required");
	AlertRuleEntity entity = alertRuleRepository.findById(ruleId)
		.orElseThrow(() -> new NotFoundException("Rule not found: " + ruleId));

	Boolean target = enabled;
	if (!target.equals(entity.getEnabled())) {
		entity.setEnabled(target);
		entity = alertRuleRepository.save(entity);
	}

	return new AlertRuleView(
		entity.getId(),
		entity.getName(),
		entity.getType(),
		entity.getConditionJson(),
		entity.getSeverity(),
		entity.getEnabled()
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
