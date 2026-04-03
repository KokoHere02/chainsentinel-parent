package com.chainsentinel.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "rule_trigger_state")
public class RuleTriggerStateEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "rule_id", nullable = false)
  private Long ruleId;

  @Column(name = "target_key", nullable = false, length = 128)
  private String targetKey;

  @Column(name = "active", nullable = false)
  private Boolean active;

  @Column(name = "last_triggered_at")
  private Instant lastTriggeredAt;

  @Column(name = "observed_value", precision = 38, scale = 18)
  private BigDecimal lastValue;

  public Long getId() {
    return id;
  }

  public Long getRuleId() {
    return ruleId;
  }

  public void setRuleId(Long ruleId) {
    this.ruleId = ruleId;
  }

  public String getTargetKey() {
    return targetKey;
  }

  public void setTargetKey(String targetKey) {
    this.targetKey = targetKey;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public Instant getLastTriggeredAt() {
    return lastTriggeredAt;
  }

  public void setLastTriggeredAt(Instant lastTriggeredAt) {
    this.lastTriggeredAt = lastTriggeredAt;
  }

  public BigDecimal getLastValue() {
    return lastValue;
  }

  public void setLastValue(BigDecimal lastValue) {
    this.lastValue = lastValue;
  }
}
