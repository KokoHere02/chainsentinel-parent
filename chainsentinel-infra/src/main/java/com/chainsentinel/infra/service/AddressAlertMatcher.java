package com.chainsentinel.infra.service;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AddressAlertMatcher {

    private final MonitorAddressRepository monitorAddressRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;

    public AddressAlertMatcher(
            MonitorAddressRepository monitorAddressRepository,
            AlertRuleRepository alertRuleRepository,
            AlertEventRepository alertEventRepository
    ) {
        this.monitorAddressRepository = monitorAddressRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.alertEventRepository = alertEventRepository;
    }

    public void evaluate(AssetEventEntity event) {
        if (event.getId() == null) {
            return;
        }
        boolean hitFrom = monitorAddressRepository.existsByChainAndAddressAndEnabledTrue(event.getChain(), lower(event.getFromAddress()));
        boolean hitTo = monitorAddressRepository.existsByChainAndAddressAndEnabledTrue(event.getChain(), lower(event.getToAddress()));
        if (!hitFrom && !hitTo) {
            return;
        }

        List<AlertRuleEntity> rules = alertRuleRepository.findByTypeAndEnabledTrue(AlertRuleType.ADDRESS);
        for (AlertRuleEntity rule : rules) {
            if (alertEventRepository.existsByRuleIdAndAssetEventId(rule.getId(), event.getId())) {
                continue;
            }
            AlertEventEntity alert = new AlertEventEntity();
            alert.setRuleId(rule.getId());
            alert.setAssetEventId(event.getId());
            alert.setSeverity(rule.getSeverity());
            alert.setSendStatus("PENDING");
            alert.setRetryCount(0);
            alertEventRepository.save(alert);
        }
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase();
    }
}
