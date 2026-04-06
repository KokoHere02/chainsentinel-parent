package com.chainsentinel.core.service.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record AlertRuleUpdateCommand(
	Long id,
	String name,
	JsonNode condition,
	String severity,
	Boolean enabled
) {
}
