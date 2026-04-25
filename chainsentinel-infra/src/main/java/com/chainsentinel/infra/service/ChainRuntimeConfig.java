package com.chainsentinel.infra.service;

record ChainRuntimeConfig(
	String chain,
	String network,
	String rpcUrl,
	int confirmRequired
) {
}

