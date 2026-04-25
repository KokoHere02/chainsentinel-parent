package com.chainsentinel.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.holding")
public class HoldingSnapshotProperties {

	private boolean enabled = true;
	private boolean snapshotScheduleEnabled = false;
	private long intervalMs = 1800000L;
	private long initialDelayMs = 15000L;
	private boolean solWsEnabled = true;
	private long solWsRefreshIntervalMs = 10000L;
	private long solWsInitialDelayMs = 5000L;
	private String nativeTokenContract = "NATIVE";
	private String nativeTokenSymbol = "ETH";
	private int nativeTokenDecimals = 18;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isSnapshotScheduleEnabled() {
		return snapshotScheduleEnabled;
	}

	public void setSnapshotScheduleEnabled(boolean snapshotScheduleEnabled) {
		this.snapshotScheduleEnabled = snapshotScheduleEnabled;
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

	public boolean isSolWsEnabled() {
		return solWsEnabled;
	}

	public void setSolWsEnabled(boolean solWsEnabled) {
		this.solWsEnabled = solWsEnabled;
	}

	public long getSolWsRefreshIntervalMs() {
		return solWsRefreshIntervalMs;
	}

	public void setSolWsRefreshIntervalMs(long solWsRefreshIntervalMs) {
		this.solWsRefreshIntervalMs = solWsRefreshIntervalMs;
	}

	public long getSolWsInitialDelayMs() {
		return solWsInitialDelayMs;
	}

	public void setSolWsInitialDelayMs(long solWsInitialDelayMs) {
		this.solWsInitialDelayMs = solWsInitialDelayMs;
	}

	public String getNativeTokenContract() {
		return nativeTokenContract;
	}

	public void setNativeTokenContract(String nativeTokenContract) {
		this.nativeTokenContract = nativeTokenContract;
	}

	public String getNativeTokenSymbol() {
		return nativeTokenSymbol;
	}

	public void setNativeTokenSymbol(String nativeTokenSymbol) {
		this.nativeTokenSymbol = nativeTokenSymbol;
	}

	public int getNativeTokenDecimals() {
		return nativeTokenDecimals;
	}

	public void setNativeTokenDecimals(int nativeTokenDecimals) {
		this.nativeTokenDecimals = nativeTokenDecimals;
	}
}
