package com.chainsentinel.core.service.dto;

import com.chainsentinel.core.model.AlertRuleType;
import java.util.Map;

public record AlertRuleCreateCommand(
        String name,
        AlertRuleType type,
        Map<String, Object> condition,
        String severity,
        Boolean enabled
) {
}
