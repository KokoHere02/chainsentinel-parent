package com.chainsentinel.price.stream;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PriceStreamStatusService {

	private final List<PriceStreamProvider> providers;

	public PriceStreamStatusService(List<PriceStreamProvider> providers) {
		this.providers = providers;
	}

	public List<PriceStreamProviderStatus> listStatuses() {
		return providers.stream()
			.filter(provider -> provider instanceof PriceStreamStatusAware)
			.map(provider -> (PriceStreamStatusAware) provider)
			.map(PriceStreamStatusAware::currentStatus)
			.toList();
	}
}