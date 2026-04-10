package com.chainsentinel.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.price.tick")
public class PriceTickIngestProperties {

	private boolean enabled = true;
	private int batchSize = 200;
	private int queueCapacity = 20000;
	private long flushIntervalMs = 1000L;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public int getQueueCapacity() {
		return queueCapacity;
	}

	public void setQueueCapacity(int queueCapacity) {
		this.queueCapacity = queueCapacity;
	}

	public long getFlushIntervalMs() {
		return flushIntervalMs;
	}

	public void setFlushIntervalMs(long flushIntervalMs) {
		this.flushIntervalMs = flushIntervalMs;
	}
}

