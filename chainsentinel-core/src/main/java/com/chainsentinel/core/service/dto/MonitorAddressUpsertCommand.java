package com.chainsentinel.core.service.dto;

public record MonitorAddressUpsertCommand(
	String chain,
	String address,
	String tag,
	Boolean enabled
) {
}
