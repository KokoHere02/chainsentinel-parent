package com.chainsentinel.core.service.dto;

public record MonitorAddressScopeView(
	Long id,
	Long monitorAddressId,
	String chain,
	String network,
	Boolean enabled
) {
}

