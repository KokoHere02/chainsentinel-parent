package com.chainsentinel.core.service.dto;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.model.TokenType;
import java.time.Instant;

public record EventView(
        Long id,
        String chain,
        String network,
        Long blockNumber,
        String txHash,
        Integer logIndex,
        String fromAddress,
        String toAddress,
        TokenType tokenType,
        String symbol,
        String amount,
        EventStatus status,
        Integer confirmations,
        Instant occurredAt
) {
}
