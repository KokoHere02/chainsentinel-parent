package com.chainsentinel.core.service.dto;

public record PriceProviderConfigCreateCommand(
	String providerName,
	String baseUrl,
	Boolean enabled,
	Integer priority,
	Integer timeoutMs
) {
}
