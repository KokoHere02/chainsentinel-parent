package com.chainsentinel.core.rule.model;

public class PriceRuleSpec {

  private int version;
  private String type;
  private PriceRuleCondition condition;

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

  public PriceRuleCondition getCondition() {
    return condition;
  }

  public void setCondition(PriceRuleCondition condition) {
    this.condition = condition;
  }
}