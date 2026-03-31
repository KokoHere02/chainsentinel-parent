package com.chainsentinel.infra.service;

import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AddressAlertMatcher {

    private static final Logger log = LoggerFactory.getLogger(AddressAlertMatcher.class);

    private static final String SEND_STATUS_PENDING = "PENDING";

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final EventRuleConditionParser ruleConditionParser;

    public AddressAlertMatcher(
            AlertRuleRepository alertRuleRepository,
            AlertEventRepository alertEventRepository,
            EventRuleConditionParser ruleConditionParser
    ) {
        this.alertRuleRepository = alertRuleRepository;
        this.alertEventRepository = alertEventRepository;
        this.ruleConditionParser = ruleConditionParser;
    }

    public void evaluate(AssetEventEntity event) {
        if (event.getId() == null) {
            return;
        }

        List<AlertRuleEntity> rules = alertRuleRepository.findByEnabledTrue();
        for (AlertRuleEntity rule : rules) {
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

            log.info("alert.match.created alertId={} ruleId={} assetEventId={} severity={}",
                    alert.getId(),
                    alert.getRuleId(),
                    alert.getAssetEventId(),
                    alert.getSeverity());
        }
    }

    private boolean isMatched(AlertRuleEntity rule, AssetEventEntity event) {
        try {
            return ruleConditionParser.matches(rule.getConditionJson(), event);
        } catch (IllegalArgumentException ex) {
            log.warn("alert.match.invalid_rule_skip ruleId={} error={}", rule.getId(), ex.getMessage());
            return false;
        }
    }
}
