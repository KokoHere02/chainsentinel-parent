package com.chainsentinel.core.service.dto;

public record MonitorAddressScopeUpsertCommand(
	Long monitorAddressId,
	String chain,
	String network,
	Boolean enabled
) {
}

