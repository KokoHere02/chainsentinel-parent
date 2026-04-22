package com.chainsentinel.core.service.dto;

public record PriceProviderConfigView(
	Long id,
	String providerName,
	String baseUrl,
	Boolean enabled,
	Integer priority,
	Integer timeoutMs
) {
}
