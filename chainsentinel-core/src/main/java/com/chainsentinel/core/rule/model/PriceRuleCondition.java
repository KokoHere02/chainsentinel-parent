package com.chainsentinel.core.rule.model;

public class PriceRuleCondition {

	private String symbol;
	private PriceRuleOperator op;
	private String threshold;
	private Integer cooldownSec;

	public String getSymbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public PriceRuleOperator getOp() {
		return op;
	}

	public void setOp(PriceRuleOperator op) {
		this.op = op;
	}

	public String getThreshold() {
		return threshold;
	}

	public void setThreshold(String threshold) {
		this.threshold = threshold;
	}

	public Integer getCooldownSec() {
		return cooldownSec;
	}

	public void setCooldownSec(Integer cooldownSec) {
		this.cooldownSec = cooldownSec;
	}
}