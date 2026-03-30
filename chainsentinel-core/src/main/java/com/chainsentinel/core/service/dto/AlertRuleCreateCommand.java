package com.chainsentinel.core.service.dto;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.rule.model.EventRuleSpec;

public record AlertRuleCreateCommand(
        String name,
        AlertRuleType type,
        EventRuleSpec condition,
        String severity,
        Boolean enabled
) {
}
