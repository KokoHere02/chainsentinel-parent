package com.chainsentinel.infra.entity;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.infra.entity.converter.AlertRuleTypeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alert_rule")
public class AlertRuleEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false, length = 128)
	private String name;

	@Column(name = "type", nullable = false, length = 32)
	@Convert(converter = AlertRuleTypeConverter.class)
	private AlertRuleType type;

	@Column(name = "condition_json", nullable = false, columnDefinition = "json")
	private String conditionJson;

	@Column(name = "severity", nullable = false, length = 16)
	private String severity;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled;

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public AlertRuleType getType() {
		return type;
	}

	public void setType(AlertRuleType type) {
		this.type = type;
	}

	public String getConditionJson() {
		return conditionJson;
	}

	public void setConditionJson(String conditionJson) {
		this.conditionJson = conditionJson;
	}

	public String getSeverity() {
		return severity;
	}

	public void setSeverity(String severity) {
		this.severity = severity;
	}

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

}
