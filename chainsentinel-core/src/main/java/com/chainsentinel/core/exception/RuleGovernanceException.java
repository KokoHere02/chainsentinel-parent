package com.chainsentinel.core.exception;

import com.chainsentinel.core.model.AlertRuleType;

public class RuleGovernanceException extends AppException {

	public RuleGovernanceException(AlertRuleType type) {
		super(
		CoreErrorCode.RULE_GOVERNANCE_REJECTED,
		400,
		"Rule type is disabled by governance: " + type
		);
	}
}
