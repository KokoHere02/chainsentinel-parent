package com.chainsentinel.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.scanner")
public class ScannerProperties {

	private boolean enabled = true;
	private long scanIntervalMs = 10000L;
	private long initialDelayMs = 3000L;
	private boolean startupRunOnReady = true;
	private int windowSize = 200;
	private long initialStartBlock = 0;
	private long reorgLookbackBlocks = 24;
	private int rpcRetryMax = 3;
	private long rpcRetryBackoffMs = 300;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public long getScanIntervalMs() {
		return scanIntervalMs;
	}

	public void setScanIntervalMs(long scanIntervalMs) {
		this.scanIntervalMs = scanIntervalMs;
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

	public int getWindowSize() {
		return windowSize;
	}

	public void setWindowSize(int windowSize) {
		this.windowSize = windowSize;
	}

	public long getInitialStartBlock() {
		return initialStartBlock;
	}

	public void setInitialStartBlock(long initialStartBlock) {
		this.initialStartBlock = initialStartBlock;
	}

	public long getReorgLookbackBlocks() {
		return reorgLookbackBlocks;
	}

	public void setReorgLookbackBlocks(long reorgLookbackBlocks) {
		this.reorgLookbackBlocks = reorgLookbackBlocks;
	}

	public int getRpcRetryMax() {
		return rpcRetryMax;
	}

	public void setRpcRetryMax(int rpcRetryMax) {
		this.rpcRetryMax = rpcRetryMax;
	}

	public long getRpcRetryBackoffMs() {
		return rpcRetryBackoffMs;
	}

	public void setRpcRetryBackoffMs(long rpcRetryBackoffMs) {
		this.rpcRetryBackoffMs = rpcRetryBackoffMs;
	}

}
