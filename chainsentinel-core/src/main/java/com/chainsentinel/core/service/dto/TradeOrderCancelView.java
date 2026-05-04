package com.chainsentinel.core.service.dto;

import java.time.Instant;

public record TradeOrderCancelView(
	Long orderId,
	String status,
	String message,
	Instant canceledAt
) {
}
