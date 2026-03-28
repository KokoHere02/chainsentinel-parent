package com.chainsentinel.core.service.dto;

import java.time.Instant;

public record AlertView(
        Long id,
        Long ruleId,
        Long assetEventId,
        String severity,
        String sendStatus,
        Integer retryCount,
        String lastError,
        Instant sentAt
) {
}
