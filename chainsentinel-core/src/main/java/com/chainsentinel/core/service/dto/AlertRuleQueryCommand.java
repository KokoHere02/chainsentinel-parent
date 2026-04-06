package com.chainsentinel.core.service.dto;

import com.chainsentinel.core.model.AlertRuleType;

public record AlertRuleQueryCommand(
	AlertRuleType type,
	Boolean enabled,
	String keyword
) {
}
