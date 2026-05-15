package com.chainsentinel.marketgateway.provider;

import com.chainsentinel.marketgateway.api.MarketGatewayException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MarketDataProviderRouter {

	private final List<MarketDataProvider> providers;

	public MarketDataProviderRouter(List<MarketDataProvider> providers) {
		this.providers = providers == null ? List.of() : List.copyOf(providers);
	}

	public MarketDataProvider resolve(String provider) {
		if (!StringUtils.hasText(provider)) {
			return resolveDefaultProvider();
		}
		for (MarketDataProvider candidate : providers) {
			if (candidate.supportsProvider(provider)) {
				return candidate;
			}
		}
		throw MarketGatewayException.badRequest("unsupported market data provider: " + provider);
	}

	public List<MarketDataProviderDescriptor> descriptors() {
		return providers.stream()
			.map(MarketDataProvider::descriptor)
			.toList();
	}

	private MarketDataProvider resolveDefaultProvider() {
		for (MarketDataProvider candidate : providers) {
			if (candidate.descriptor().status() == MarketDataProviderStatus.UP) {
				return candidate;
			}
		}
		if (!providers.isEmpty()) {
			return providers.get(0);
		}
		throw MarketGatewayException.badRequest("no market data provider available");
	}
}
