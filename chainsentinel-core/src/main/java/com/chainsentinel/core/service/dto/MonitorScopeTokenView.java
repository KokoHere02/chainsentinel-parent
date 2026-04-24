package com.chainsentinel.core.service.dto;

public record MonitorScopeTokenView(
	Long id,
	Long monitorScopeId,
	String tokenContract,
	String symbol,
	Integer decimals,
	Boolean enabled
) {
}

