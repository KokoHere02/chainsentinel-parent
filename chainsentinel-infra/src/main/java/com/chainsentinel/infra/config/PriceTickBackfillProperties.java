package com.chainsentinel.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.price.tick-backfill")
public class PriceTickBackfillProperties {

	private int retentionDays = 30;
	private String bar = "1m";
	private int pageLimit = 300;
	private int maxRounds = 1000;
	private long sleepMs = 50L;
	private int globalMaxConcurrent = 1;

	public int getRetentionDays() {
		return retentionDays;
	}

	public void setRetentionDays(int retentionDays) {
		this.retentionDays = retentionDays;
	}

	public String getBar() {
		return bar;
	}

	public void setBar(String bar) {
		this.bar = bar;
	}

	public int getPageLimit() {
		return pageLimit;
	}

	public void setPageLimit(int pageLimit) {
		this.pageLimit = pageLimit;
	}

	public int getMaxRounds() {
		return maxRounds;
	}

	public void setMaxRounds(int maxRounds) {
		this.maxRounds = maxRounds;
	}

	public long getSleepMs() {
		return sleepMs;
	}

	public void setSleepMs(long sleepMs) {
		this.sleepMs = sleepMs;
	}

	public int getGlobalMaxConcurrent() {
		return globalMaxConcurrent;
	}

	public void setGlobalMaxConcurrent(int globalMaxConcurrent) {
		this.globalMaxConcurrent = globalMaxConcurrent;
	}
}