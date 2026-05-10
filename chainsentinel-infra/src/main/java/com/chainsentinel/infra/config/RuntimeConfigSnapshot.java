package com.chainsentinel.infra.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RuntimeConfigSnapshot(
	Map<String, RuntimeConfigSnapshot.ProviderConfigSnapshot> providers,
	Map<String, Integer> priorities
) {

	public RuntimeConfigSnapshot {
		providers = Collections.unmodifiableMap(new LinkedHashMap<>(providers));
		priorities = Collections.unmodifiableMap(new LinkedHashMap<>(priorities));
	}

	public static RuntimeConfigSnapshot empty() {
		return new RuntimeConfigSnapshot(Map.of(), Map.of());
	}

	public record ProviderConfigSnapshot(
		String providerName,
		String baseUrl,
		Integer priority,
		Integer timeoutMs
	) {
	}
}
