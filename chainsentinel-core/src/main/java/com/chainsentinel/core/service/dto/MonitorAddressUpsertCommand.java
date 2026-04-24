package com.chainsentinel.core.service.dto;

public record MonitorAddressUpsertCommand(
	String address,
	String tag,
	Boolean enabled
) {
}
