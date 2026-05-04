package com.chainsentinel.infra.service;

import java.time.Instant;

public record TradeOrderStreamStatus(
	Long accountId,
	String provider,
	Boolean enabled,
	Boolean connected,
	Boolean loggedIn,
	Boolean orderSubscribed,
	Boolean assetSubscribed,
	Instant lastMessageAt,
	Instant lastOrderMessageAt,
	Instant lastAssetMessageAt,
	String lastErrorType,
	String lastErrorMessage
) {
}
