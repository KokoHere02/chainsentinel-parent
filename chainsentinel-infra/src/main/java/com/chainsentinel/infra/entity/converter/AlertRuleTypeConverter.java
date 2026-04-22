package com.chainsentinel.infra.entity.converter;

import com.chainsentinel.core.model.AlertRuleType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AlertRuleTypeConverter implements AttributeConverter<AlertRuleType, String> {

	@Override
	public String convertToDatabaseColumn(AlertRuleType attribute) {
		return attribute == null ? null : attribute.name();
	}

	@Override
	public AlertRuleType convertToEntityAttribute(String dbData) {
		if (dbData == null) {
			return null;
		}
		return AlertRuleType.fromValue(dbData);
	}
}
