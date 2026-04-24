package com.chainsentinel.core.service.dto;

import java.time.Instant;

public record AddressTokenHoldingView(
	Long id,
	Long monitorScopeId,
	String chain,
	String network,
	String address,
	String tokenContract,
	String tokenSymbol,
	Integer decimals,
	String balanceRaw,
	Instant balanceUpdatedAt
) {
}
