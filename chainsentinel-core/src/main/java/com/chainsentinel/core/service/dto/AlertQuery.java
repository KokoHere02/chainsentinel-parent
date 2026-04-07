package com.chainsentinel.core.service.dto;

import java.time.Instant;

public record AlertQuery(
	String sendStatus,
	String severity,
	Long ruleId,
	Instant sentAtFrom,
	Instant sentAtTo
) {
}
