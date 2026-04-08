package com.chainsentinel.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.price.ws")
public class PriceStreamProperties {

	private boolean enabled = true;
	private long refreshIntervalMs = 30000L;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public long getRefreshIntervalMs() {
		return refreshIntervalMs;
	}

	public void setRefreshIntervalMs(long refreshIntervalMs) {
		this.refreshIntervalMs = refreshIntervalMs;
	}

}
