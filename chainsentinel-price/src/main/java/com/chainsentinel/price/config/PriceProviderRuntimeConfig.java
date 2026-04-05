package com.chainsentinel.price.config;

import java.util.Map;

public interface PriceProviderRuntimeConfig {

	Map<String, Integer> providerPriority();

	boolean providerEnabled(String providerName);

	String providerBaseUrl(String providerName, String defaultBaseUrl);

	int providerTimeoutMs(String providerName, int defaultTimeoutMs);

	/**
	* Manually refreshes local runtime-config cache.
	* Default implementation is no-op so non-cached implementations stay compatible.
	*/
	default void refreshCache() {
		// no-op
	}
}
