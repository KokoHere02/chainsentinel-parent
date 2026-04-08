package com.chainsentinel.infra.service;

import java.util.List;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

@Component
public class AddressAlertMatcher {

	private static final Logger log = LoggerFactory.getLogger(AddressAlertMatcher.class);

	private static final String SEND_STATUS_PENDING = "PENDING";
	private static final String METRIC_RULE_EVAL_FAIL_TOTAL = "rule_eval_fail_total";

	private final AlertRuleRepository alertRuleRepository;
	private final AlertEventRepository alertEventRepository;
	private final EventRuleConditionParser ruleConditionParser;
	private final MeterRegistry meterRegistry;

	public AddressAlertMatcher(
		AlertRuleRepository alertRuleRepository,
		AlertEventRepository alertEventRepository,
		EventRuleConditionParser ruleConditionParser,
		MeterRegistry meterRegistry
	) {
		this.alertRuleRepository = alertRuleRepository;
		this.alertEventRepository = alertEventRepository;
		this.ruleConditionParser = ruleConditionParser;
		this.meterRegistry = meterRegistry;
	}

	public void evaluate(AssetEventEntity event) {
		if (event.getId() == null) {
			return;
		}

		List<AlertRuleEntity> rules = alertRuleRepository.findByEnabledTrue();
		for (AlertRuleEntity rule : rules) {
			if (!isSupportedRuleType(rule.getType())) {
				log.debug("alert.match.unsupported_type_skip ruleId={} type={}", rule.getId(), rule.getType());
				continue;
			}
			if (!isMatched(rule, event)) {
				continue;
			}
			if (alertEventRepository.existsByRuleIdAndAssetEventId(rule.getId(), event.getId())) {
				log.debug("alert.match.duplicate_skip ruleId={} assetEventId={}", rule.getId(), event.getId());
				continue;
			}

			AlertEventEntity alert = new AlertEventEntity();
			alert.setRuleId(rule.getId());
			alert.setAssetEventId(event.getId());
			alert.setSeverity(rule.getSeverity());
			alert.setSendStatus(SEND_STATUS_PENDING);
			alert.setRetryCount(0);
			alertEventRepository.save(alert);

			log.info("alert.match.created alertId={} ruleId={} assetEventId={} severity={} type={}",
				alert.getId(),
				alert.getRuleId(),
				alert.getAssetEventId(),
				alert.getSeverity(),
				rule.getType());
		}
	}

	private boolean isSupportedRuleType(AlertRuleType type) {
		return type == AlertRuleType.ADDRESS || type == AlertRuleType.AMOUNT;
	}

	private boolean isMatched(AlertRuleEntity rule, AssetEventEntity event) {
		try {
			return ruleConditionParser.matches(rule.getConditionJson(), event);
		} catch (IllegalArgumentException ex) {
			recordRuleEvalFail(rule, "invalid");
			log.warn("rule.eval.invalid ruleId={} type={} error={}",
				rule.getId(),
				rule.getType(),
				ex.getMessage());
			return false;
		} catch (Exception ex) {
			recordRuleEvalFail(rule, "error");
			log.error("rule.eval.error ruleId={} type={} error={}",
				rule.getId(),
				rule.getType(),
				ex.getMessage(),
				ex);
			return false;
		}
	}

	private void recordRuleEvalFail(AlertRuleEntity rule, String reason) {
		meterRegistry.counter(
			METRIC_RULE_EVAL_FAIL_TOTAL,
			"ruleId", String.valueOf(rule.getId()),
			"type", String.valueOf(rule.getType()),
			"reason", reason
		).increment();
	}

}
