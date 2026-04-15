package com.chainsentinel.core.service.dto;

import com.chainsentinel.core.model.EventStatus;
import java.time.Instant;

public record EventQuery(
	String chain,
	String network,
	String address,
	String fromAddress,
	String toAddress,
	String symbol,
	String txHash,
	EventStatus status,
	Instant startTime,
	Instant endTime
) {
	public EventQuery(String chain, String address, EventStatus status, Instant startTime, Instant endTime) {
		this(chain, null, address, null, null, null, null, status, startTime, endTime);
	}
}