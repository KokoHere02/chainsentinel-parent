package com.chainsentinel.price.stream;

import java.time.Instant;

public record PriceStreamProviderStatus(
	String provider,
	boolean started,
	boolean connected,
	boolean reconnectScheduled,
	int reconnectAttempts,
	String lastReconnectReason,
	Instant lastReconnectAt,
	String lastErrorType,
	String lastErrorMessage,
	Instant lastErrorAt,
	int lastResubscribeCount,
	Instant lastResubscribeAt,
	int cachedQueryCount
) {
}