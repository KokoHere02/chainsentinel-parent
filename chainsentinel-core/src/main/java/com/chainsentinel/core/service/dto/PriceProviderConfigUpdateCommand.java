package com.chainsentinel.core.service.dto;

public record PriceProviderConfigUpdateCommand(
	String providerName,
	String baseUrl,
	Boolean enabled,
	Integer priority,
	Integer timeoutMs
) {
}
