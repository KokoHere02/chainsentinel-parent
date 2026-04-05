package com.chainsentinel.core.rule.model;

import java.util.List;

public class EventRuleCondition {

	private List<EventRuleConditionItem> all;

	public EventRuleCondition() {
	}

	public EventRuleCondition(List<EventRuleConditionItem> all) {
		this.all = all;
	}

	public List<EventRuleConditionItem> getAll() {
		return all;
	}

	public void setAll(List<EventRuleConditionItem> all) {
		this.all = all;
	}
}
