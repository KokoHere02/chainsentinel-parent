package com.chainsentinel.core.service.dto;

public record MonitorAddressView(
	Long id,
	String address,
	String tag,
	Boolean enabled
) {
}
