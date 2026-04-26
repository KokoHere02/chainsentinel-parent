package com.chainsentinel.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.holding")
public class HoldingSnapshotProperties {

	private boolean solWsEnabled = true;
	private String nativeTokenContract = "NATIVE";
	private String nativeTokenSymbol = "ETH";
	private int nativeTokenDecimals = 18;

	public boolean isSolWsEnabled() {
		return solWsEnabled;
	}

	public void setSolWsEnabled(boolean solWsEnabled) {
		this.solWsEnabled = solWsEnabled;
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
