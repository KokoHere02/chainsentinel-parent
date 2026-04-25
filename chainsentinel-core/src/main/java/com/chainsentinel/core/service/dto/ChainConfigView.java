package com.chainsentinel.core.service.dto;

public record ChainConfigView(
	Long id,
	String chain,
	String network,
	String rpcUrl,
	String rpcHttpUrl,
	String rpcWsUrl,
	String balanceProtocol,
	Integer confirmRequired,
	Boolean enabled
) {
}
