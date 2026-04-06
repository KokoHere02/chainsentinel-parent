package com.chainsentinel.core.service.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record AlertRulePatchConditionCommand(
	Long id,
	JsonNode condition
) {
}
