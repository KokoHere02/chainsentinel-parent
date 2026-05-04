package com.chainsentinel.core.service.dto;

public record TradeAccountCreateCommand(
	String name,
	String provider,
	String accountType,
	String envType,
	String apiKey,
	String apiSecret,
	String passphrase,
	Boolean enabled,
	String remark
) {
}
