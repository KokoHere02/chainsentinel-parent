package com.chainsentinel.infra.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.cache.runtime-config")
public class RuntimeConfigCacheProperties {

	private boolean l2Enabled;
	private boolean invalidationEnabled = true;
	private Duration l1Ttl = Duration.ofSeconds(10);
	private Duration l2Ttl = Duration.ofSeconds(90);
	private double l2TtlJitterRatio = 0.1d;
	private int l2DeleteRetryMax = 3;
	private Duration l2DeleteRetryBackoff = Duration.ofMillis(50);
	private String redisKeyPrefix = "cs:price:runtime:";
	private String invalidationChannel = "cs:cache:invalidate:price-runtime-config";

	public boolean isL2Enabled() {
		return l2Enabled;
	}

	public void setL2Enabled(boolean l2Enabled) {
		this.l2Enabled = l2Enabled;
	}

	public boolean isInvalidationEnabled() {
		return invalidationEnabled;
	}

	public void setInvalidationEnabled(boolean invalidationEnabled) {
		this.invalidationEnabled = invalidationEnabled;
	}

	public Duration getL1Ttl() {
		return l1Ttl;
	}

	public void setL1Ttl(Duration l1Ttl) {
		this.l1Ttl = l1Ttl;
	}

	public Duration getL2Ttl() {
		return l2Ttl;
	}

	public void setL2Ttl(Duration l2Ttl) {
		this.l2Ttl = l2Ttl;
	}

	public double getL2TtlJitterRatio() {
		return l2TtlJitterRatio;
	}

	public void setL2TtlJitterRatio(double l2TtlJitterRatio) {
		this.l2TtlJitterRatio = l2TtlJitterRatio;
	}

	public int getL2DeleteRetryMax() {
		return l2DeleteRetryMax;
	}

	public void setL2DeleteRetryMax(int l2DeleteRetryMax) {
		this.l2DeleteRetryMax = l2DeleteRetryMax;
	}

	public Duration getL2DeleteRetryBackoff() {
		return l2DeleteRetryBackoff;
	}

	public void setL2DeleteRetryBackoff(Duration l2DeleteRetryBackoff) {
		this.l2DeleteRetryBackoff = l2DeleteRetryBackoff;
	}

	public String getRedisKeyPrefix() {
		return redisKeyPrefix;
	}

	public void setRedisKeyPrefix(String redisKeyPrefix) {
		this.redisKeyPrefix = redisKeyPrefix;
	}

	public String getInvalidationChannel() {
		return invalidationChannel;
	}

	public void setInvalidationChannel(String invalidationChannel) {
		this.invalidationChannel = invalidationChannel;
	}

	public String snapshotKey() {
		return redisKeyPrefix + "snapshot";
	}
}
