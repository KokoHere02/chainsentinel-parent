package com.chainsentinel.core.service.dto;

public record AlertQuery(
        String sendStatus,
        String severity,
        Long ruleId
) {
}
