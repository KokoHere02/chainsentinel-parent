package com.chainsentinel.core.service.dto;

public record ChainConfigView(
	Long id,
	String chain,
	String network,
	String rpcUrl,
	Integer confirmRequired,
	Boolean enabled
) {
}
