package com.chainsentinel.core.service.dto;

public record ChainConfigUpsertCommand(
        String chain,
        String network,
        String rpcUrl,
        Integer confirmRequired,
        Boolean enabled
) {
}
