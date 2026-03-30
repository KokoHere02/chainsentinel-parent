package com.chainsentinel.core.rule.model;

public class EventRuleSpec {

    private int version;
    private String type;
    private EventRuleCondition condition;

    public EventRuleSpec() {
    }

    public EventRuleSpec(int version, String type, EventRuleCondition condition) {
        this.version = version;
        this.type = type;
        this.condition = condition;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public EventRuleCondition getCondition() {
        return condition;
    }

    public void setCondition(EventRuleCondition condition) {
        this.condition = condition;
    }
}
