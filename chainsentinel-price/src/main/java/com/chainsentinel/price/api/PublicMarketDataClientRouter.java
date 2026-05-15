package com.chainsentinel.price.api;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PublicMarketDataClientRouter {

	private final List<PublicMarketDataClient> clients;

	public PublicMarketDataClientRouter(List<PublicMarketDataClient> clients) {
		this.clients = clients == null ? List.of() : List.copyOf(clients);
	}

	public PublicMarketDataClient resolve(String provider) {
		for (PublicMarketDataClient client : clients) {
			if (client.supportsProvider(provider)) {
				return client;
			}
		}
		throw new IllegalArgumentException("unsupported public market data provider: " + provider);
	}
}
