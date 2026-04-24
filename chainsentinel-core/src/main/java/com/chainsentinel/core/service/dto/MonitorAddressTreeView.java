package com.chainsentinel.core.service.dto;

import java.util.List;

public record MonitorAddressTreeView(
	Long id,
	String address,
	String tag,
	Boolean enabled,
	List<ScopeNode> scopes
) {

	public record ScopeNode(
		Long id,
		String chain,
		String network,
		Boolean enabled,
		List<TokenNode> tokens
	) {
	}

	public record TokenNode(
		Long id,
		String tokenContract,
		String symbol,
		Integer decimals,
		Boolean enabled
	) {
	}
}

