package com.chainsentinel.core.rule.model;

public class EventRuleConditionItem {

    private EventRuleField field;
    private EventRuleOperator op;
    private Object value;

    public EventRuleConditionItem() {
    }

    public EventRuleConditionItem(EventRuleField field, EventRuleOperator op, Object value) {
        this.field = field;
        this.op = op;
        this.value = value;
    }

    public EventRuleField getField() {
        return field;
    }

    public void setField(EventRuleField field) {
        this.field = field;
    }

    public EventRuleOperator getOp() {
        return op;
    }

    public void setOp(EventRuleOperator op) {
        this.op = op;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
