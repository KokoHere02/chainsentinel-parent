package com.chainsentinel.core.service.dto;

public record MonitorTokenView(Long id, String chain, String tokenContract, String symbol, Boolean enabled) {
}