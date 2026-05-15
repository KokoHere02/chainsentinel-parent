package com.chainsentinel.marketgateway.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.price.market-data.gateway")
public class MarketDataGatewayProperties {

	private boolean enabled;
	private String providerName = "market_gateway";
	private String baseUrl = "http://localhost:18080";
	private int timeoutMs = 3000;
	private String internalToken;
	private String internalTokenHeaderName = "X-Internal-Token";
	private List<String> aliases = new ArrayList<>(List.of("market_gateway", "gateway"));

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getProviderName() {
		return providerName;
	}

	public void setProviderName(String providerName) {
		this.providerName = providerName;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public int getTimeoutMs() {
		return timeoutMs;
	}

	public void setTimeoutMs(int timeoutMs) {
		this.timeoutMs = timeoutMs;
	}

	public String getInternalToken() {
		return internalToken;
	}

	public void setInternalToken(String internalToken) {
		this.internalToken = internalToken;
	}

	public String getInternalTokenHeaderName() {
		return internalTokenHeaderName;
	}

	public void setInternalTokenHeaderName(String internalTokenHeaderName) {
		this.internalTokenHeaderName = internalTokenHeaderName;
	}

	public List<String> getAliases() {
		return aliases;
	}

	public void setAliases(List<String> aliases) {
		this.aliases = aliases == null ? new ArrayList<>() : new ArrayList<>(aliases);
	}
}
