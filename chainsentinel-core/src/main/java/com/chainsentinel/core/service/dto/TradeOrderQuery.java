package com.chainsentinel.core.service.dto;

public record TradeOrderQuery(
	Long accountId,
	String status,
	String symbol,
	Integer limit
) {
}
