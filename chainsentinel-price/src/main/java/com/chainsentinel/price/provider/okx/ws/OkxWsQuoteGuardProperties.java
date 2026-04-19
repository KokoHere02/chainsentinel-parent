package com.chainsentinel.price.provider.okx.ws;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.price.ws.okx.guard")
public class OkxWsQuoteGuardProperties {

	private long shortWindowMs = 2000L;
	private double maxJumpRatio = 0.20D;
	private long dropLogIntervalMs = 30000L;

	public long getShortWindowMs() {
		return shortWindowMs;
	}

	public void setShortWindowMs(long shortWindowMs) {
		this.shortWindowMs = shortWindowMs;
	}

	public double getMaxJumpRatio() {
		return maxJumpRatio;
	}

	public void setMaxJumpRatio(double maxJumpRatio) {
		this.maxJumpRatio = maxJumpRatio;
	}

	public long getDropLogIntervalMs() {
		return dropLogIntervalMs;
	}

	public void setDropLogIntervalMs(long dropLogIntervalMs) {
		this.dropLogIntervalMs = dropLogIntervalMs;
	}
}
