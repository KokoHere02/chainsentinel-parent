package com.chainsentinel.core.service.dto;

import com.chainsentinel.core.model.AlertRuleType;

public record AlertRuleView(
	Long id,
	String name,
	AlertRuleType type,
	String conditionJson,
	String severity,
	Boolean enabled
) {
}
