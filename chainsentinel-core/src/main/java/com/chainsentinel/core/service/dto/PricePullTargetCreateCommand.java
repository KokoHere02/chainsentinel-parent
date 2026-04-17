package com.chainsentinel.core.service.dto;

public record PricePullTargetCreateCommand(
	Long assetId,
	Long providerConfigId,
	String instType,
	String instId,
	String quoteSymbol,
	Boolean enabled,
	Integer pollIntervalMs,
	Integer priority
) {
}