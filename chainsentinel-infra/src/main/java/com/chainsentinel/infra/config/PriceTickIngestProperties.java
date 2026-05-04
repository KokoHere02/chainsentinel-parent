package com.chainsentinel.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.price.tick")
public class PriceTickIngestProperties {

	private boolean enabled = true;
	private int batchSize = 200;
	private int queueCapacity = 20000;
	private long flushIntervalMs = 1000L;
	private int highWatermark = 0;
	private double minPersistChangeRatio = 0.0D;

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

	public int getHighWatermark() {
		return highWatermark;
	}

	public void setHighWatermark(int highWatermark) {
		this.highWatermark = highWatermark;
	}

	public double getMinPersistChangeRatio() {
		return minPersistChangeRatio;
	}

	public void setMinPersistChangeRatio(double minPersistChangeRatio) {
		this.minPersistChangeRatio = minPersistChangeRatio;
	}
}
