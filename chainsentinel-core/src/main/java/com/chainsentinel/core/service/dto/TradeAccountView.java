package com.chainsentinel.core.service.dto;

public record TradeAccountView(
	Long id,
	String name,
	String provider,
	String accountType,
	String envType,
	String apiKeyMasked,
	Boolean hasApiSecret,
	Boolean hasPassphrase,
	Boolean enabled,
	String remark,
	Long createdBy,
	Long updatedBy
) {
}
