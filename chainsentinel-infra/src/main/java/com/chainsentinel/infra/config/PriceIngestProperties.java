package com.chainsentinel.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.price.ingest")
public class PriceIngestProperties {

	private boolean enabled = true;
	private long intervalMs = 15000L;
	private long initialDelayMs = 5000L;
	private boolean startupRunOnReady = false;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public long getIntervalMs() {
		return intervalMs;
	}

	public void setIntervalMs(long intervalMs) {
		this.intervalMs = intervalMs;
	}

	public long getInitialDelayMs() {
		return initialDelayMs;
	}

	public void setInitialDelayMs(long initialDelayMs) {
		this.initialDelayMs = initialDelayMs;
	}

	public boolean isStartupRunOnReady() {
		return startupRunOnReady;
	}

	public void setStartupRunOnReady(boolean startupRunOnReady) {
		this.startupRunOnReady = startupRunOnReady;
	}

}
