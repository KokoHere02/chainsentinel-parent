package com.chainsentinel.infra.rule;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.chainsentinel.core.rule.model.EventRuleCondition;
import com.chainsentinel.core.rule.model.EventRuleConditionItem;
import com.chainsentinel.core.rule.model.EventRuleField;
import com.chainsentinel.core.rule.model.EventRuleOperator;
import com.chainsentinel.core.rule.model.EventRuleSpec;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EventRuleConditionParser {

	private final ObjectMapper objectMapper;

	public EventRuleConditionParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public EventRuleSpec parse(String conditionJson) {
		if (!StringUtils.hasText(conditionJson)) {
			throw new IllegalArgumentException("condition_json is blank");
		}
		try {
			EventRuleSpec spec = objectMapper.readValue(conditionJson, EventRuleSpec.class);
			validate(spec);
			return spec;
		} catch (Exception ex) {
			if (ex instanceof IllegalArgumentException iae) {
				throw iae;
			}
			throw new IllegalArgumentException("Invalid condition_json", ex);
		}
	}

	public String serialize(EventRuleSpec spec) {
		validate(spec);
		try {
			return objectMapper.writeValueAsString(spec);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Invalid rule object", ex);
		}
	}

	public boolean matches(String conditionJson, AssetEventEntity event) {
		return matches(parse(conditionJson), event);
	}

	public boolean matches(EventRuleSpec spec, AssetEventEntity event) {
		validate(spec);
		for (EventRuleConditionItem item : spec.getCondition().getAll()) {
			if (!matches(item, event)) {
				return false;
			}
		}
		return true;
	}

	private void validate(EventRuleSpec spec) {
		if (spec == null) {
			throw new IllegalArgumentException("Rule is null");
		}
		if (spec.getVersion() != 1) {
			throw new IllegalArgumentException("Unsupported version: " + spec.getVersion());
		}
		if (!"EVENT".equalsIgnoreCase(spec.getType())) {
			throw new IllegalArgumentException("Unsupported type: " + spec.getType());
		}

		EventRuleCondition condition = spec.getCondition();
		if (condition == null || condition.getAll() == null || condition.getAll().isEmpty()) {
			throw new IllegalArgumentException("condition.all must be a non-empty array");
		}

		for (EventRuleConditionItem item : condition.getAll()) {
			validateItem(item);
		}
	}

	private void validateItem(EventRuleConditionItem item) {
		if (item == null) {
			throw new IllegalArgumentException("Condition item is null");
		}
		if (item.getField() == null) {
			throw new IllegalArgumentException("field is required");
		}
		if (item.getOp() == null) {
			throw new IllegalArgumentException("op is required");
		}
		if (item.getValue() == null) {
			throw new IllegalArgumentException("value is required");
		}

		boolean multiValueOp = item.getOp() == EventRuleOperator.IN || item.getOp() == EventRuleOperator.NOT_IN;
		if (multiValueOp && !isMultiValue(item.getValue())) {
			throw new IllegalArgumentException("value must be array for op " + item.getOp().wireValue());
		}
		if (!multiValueOp && isMultiValue(item.getValue())) {
			throw new IllegalArgumentException("value must be scalar for op " + item.getOp().wireValue());
		}
	}

	private boolean matches(EventRuleConditionItem item, AssetEventEntity event) {
		Object left = fieldValue(item.getField(), event);
		EventRuleOperator op = item.getOp();

		if (op == EventRuleOperator.IN || op == EventRuleOperator.NOT_IN) {
			List<?> values = toList(item.getValue());
			boolean contains = containsByField(item.getField(), left, values);
			return (op == EventRuleOperator.IN) == contains;
		}

		if (op == EventRuleOperator.EQ || op == EventRuleOperator.NE) {
			boolean equal = equalsByField(item.getField(), left, item.getValue());
			return (op == EventRuleOperator.EQ) == equal;
		}

		int cmp = compareByField(item.getField(), left, item.getValue());
		return switch (op) {
			case GT -> cmp > 0;
			case GTE -> cmp >= 0;
			case LT -> cmp < 0;
			case LTE -> cmp <= 0;
			default -> throw new IllegalArgumentException("Unsupported op: " + op.wireValue());
		};
	}

	private boolean containsByField(EventRuleField field, Object left, List<?> values) {
		if (field == EventRuleField.AMOUNT) {
			BigInteger actual = toAmountValue(left);
			return values.stream().map(this::toAmountValue).anyMatch(actual::equals);
		}
		String actual = normalizeText(field, toText(left));
		return values.stream().map(v -> normalizeText(field, toText(v))).anyMatch(actual::equals);
	}

	private boolean equalsByField(EventRuleField field, Object left, Object right) {
		if (field == EventRuleField.AMOUNT) {
			return toAmountValue(left).compareTo(toAmountValue(right)) == 0;
		}
		return normalizeText(field, toText(left)).equals(normalizeText(field, toText(right)));
	}

	private int compareByField(EventRuleField field, Object left, Object right) {
		if (field == EventRuleField.AMOUNT) {
			return toAmountValue(left).compareTo(toAmountValue(right));
		}
		return toDecimal(left).compareTo(toDecimal(right));
	}

	private BigInteger toAmountValue(Object value) {
		return AmountComparisonValueConverter.toComparisonValue(value);
	}

	private Object fieldValue(EventRuleField field, AssetEventEntity event) {
		return switch (field) {
			case CHAIN -> event.getChain();
			case NETWORK -> event.getNetwork();
			case FROM_ADDRESS -> event.getFromAddress();
			case TO_ADDRESS -> event.getToAddress();
			case TOKEN_TYPE -> event.getTokenType() == null ? null : event.getTokenType().name();
			case TOKEN_CONTRACT -> event.getTokenContract();
			case SYMBOL -> event.getSymbol();
			case AMOUNT -> event.getAmount();
			case STATUS -> event.getStatus() == null ? null : event.getStatus().name();
		};
	}

	private boolean isMultiValue(Object value) {
		return value.getClass().isArray() || value instanceof Collection<?>;
	}

	private List<?> toList(Object value) {
		if (value instanceof List<?> list) {
			return list;
		}
		if (value instanceof Collection<?> collection) {
			return collection.stream().toList();
		}
		if (value.getClass().isArray()) {
			return java.util.Arrays.asList((Object[]) value);
		}
		throw new IllegalArgumentException("value is not array");
	}

	private BigDecimal toDecimal(Object value) {
		if (value == null) {
			return BigDecimal.ZERO;
		}
		return new BigDecimal(String.valueOf(value));
	}

	private String normalizeText(EventRuleField field, String value) {
		if (value == null) {
			return "";
		}
		String normalized = value.trim();
		if (field == EventRuleField.FROM_ADDRESS || field == EventRuleField.TO_ADDRESS || field == EventRuleField.TOKEN_CONTRACT) {
			return normalized.toLowerCase(Locale.ROOT);
		}
		return normalized;
	}

	private String toText(Object value) {
		return value == null ? "" : String.valueOf(value);
	}
}
