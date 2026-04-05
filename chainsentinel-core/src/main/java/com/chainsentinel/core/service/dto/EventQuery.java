package com.chainsentinel.core.service.dto;

import com.chainsentinel.core.model.EventStatus;
import java.time.Instant;

public record EventQuery(
	String chain,
	String address,
	EventStatus status,
	Instant startTime,
	Instant endTime
) {
}

