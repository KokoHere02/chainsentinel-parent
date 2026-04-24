package com.chainsentinel.core.service.dto;

public record MonitorScopeTokenUpsertCommand(
	Long monitorScopeId,
	String tokenContract,
	String symbol,
	Integer decimals,
	Boolean enabled
) {
}

