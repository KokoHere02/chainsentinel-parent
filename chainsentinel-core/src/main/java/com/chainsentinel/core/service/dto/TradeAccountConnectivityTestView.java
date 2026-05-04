package com.chainsentinel.core.service.dto;

import java.time.Instant;

public record TradeAccountConnectivityTestView(
	Long accountId,
	String provider,
	Boolean success,
	String message,
	Instant checkedAt
) {
}
