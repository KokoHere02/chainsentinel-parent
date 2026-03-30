package com.chainsentinel.core.rule.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum EventRuleOperator {
    EQ("eq"),
    NE("ne"),
    GT("gt"),
    GTE("gte"),
    LT("lt"),
    LTE("lte"),
    IN("in"),
    NOT_IN("not_in");

    private final String wireValue;

    EventRuleOperator(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static EventRuleOperator fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (EventRuleOperator op : values()) {
            if (op.wireValue.equals(normalized)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unsupported op: " + value);
    }
}
