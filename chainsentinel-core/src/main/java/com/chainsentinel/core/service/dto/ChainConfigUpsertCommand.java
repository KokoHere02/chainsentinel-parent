package com.chainsentinel.core.service.dto;

public record ChainConfigUpsertCommand(
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
