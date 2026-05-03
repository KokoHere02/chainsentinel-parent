package com.chainsentinel.infra.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
import com.chainsentinel.infra.support.ManagementQueryPageSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultAlertRuleService implements AlertRuleService {

	private static final Logger log = LoggerFactory.getLogger(DefaultAlertRuleService.class);
	private static final int DEFAULT_PAGE_SIZE = 100;
	private static final Set<String> FORBIDDEN_EVENT_FIELDS = Set.of("from_address", "to_address");
	private static final Set<AlertRuleType> ENABLED_RULE_TYPES = EnumSet.of(
		AlertRuleType.EVENT,
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
		validateNoAddressFields(command.type(), command.condition());

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
		validateNoAddressFields(entity.getType(), command.condition());

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
		validateNoAddressFields(entity.getType(), command.condition());

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
		return list(command, 0, DEFAULT_PAGE_SIZE);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AlertRuleView> list(AlertRuleQueryCommand command, int page, int size) {
		String keyword = command == null ? null : command.keyword();
		AlertRuleType type = command == null ? null : command.type();
		Boolean enabled = command == null ? null : command.enabled();
		String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;

		return alertRuleRepository.listByFilters(
				type,
				enabled,
				normalizedKeyword,
				ManagementQueryPageSupport.pageByIdDesc(page, size)
			)
			.stream()
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

	private void validateNoAddressFields(AlertRuleType type, JsonNode condition) {
		if (type != AlertRuleType.EVENT || condition == null || condition.isNull()) {
			return;
		}
		JsonNode all = condition.path("condition").path("all");
		if (!all.isArray()) {
			return;
		}
		for (JsonNode item : all) {
			if (item == null || !item.isObject()) {
				continue;
			}
			String field = item.path("field").asText("");
			if (!StringUtils.hasText(field)) {
				continue;
			}
			String normalized = field.trim().toLowerCase();
			if (FORBIDDEN_EVENT_FIELDS.contains(normalized)) {
				throw new IllegalArgumentException("Event rule must not contain address field: " + normalized);
			}
		}
	}

}
