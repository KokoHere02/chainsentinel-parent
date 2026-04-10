package com.chainsentinel.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.price.tick-retention")
public class PriceTickRetentionProperties {

	private boolean enabled = true;
	private int retentionDays = 30;
	private long cleanupIntervalMs = 3600000L;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getRetentionDays() {
		return retentionDays;
	}

	public void setRetentionDays(int retentionDays) {
		this.retentionDays = retentionDays;
	}

	public long getCleanupIntervalMs() {
		return cleanupIntervalMs;
	}

	public void setCleanupIntervalMs(long cleanupIntervalMs) {
		this.cleanupIntervalMs = cleanupIntervalMs;
	}
}

