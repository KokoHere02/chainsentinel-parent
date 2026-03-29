package com.chainsentinel.core.service.dto;

public record MonitorTokenUpsertCommand(String chain, String tokenContract, String symbol, Boolean enabled) {
}