package com.chainsentinel.infra.service;

public interface ChainEventScanner {

	boolean supports(String chain);

	int scan(ChainRuntimeConfig runtime, RuntimeWatchers watchers);
}

