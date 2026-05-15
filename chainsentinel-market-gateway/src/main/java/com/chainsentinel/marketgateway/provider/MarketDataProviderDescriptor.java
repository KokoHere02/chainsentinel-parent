package com.chainsentinel.marketgateway.provider;

import java.util.List;

public record MarketDataProviderDescriptor(
	String provider,
	MarketDataProviderStatus status,
	List<MarketDataCapability> capabilities,
	String message
) {
}
