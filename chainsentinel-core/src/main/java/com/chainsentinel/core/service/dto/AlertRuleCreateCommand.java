package com.chainsentinel.core.service.dto;

import com.chainsentinel.core.model.AlertRuleType;
import com.fasterxml.jackson.databind.JsonNode;

public record AlertRuleCreateCommand(
  String name,
  AlertRuleType type,
  JsonNode condition,
  String severity,
  Boolean enabled
) {
}