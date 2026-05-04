package com.chainsentinel.infra.service;

import java.math.BigDecimal;

public record TradeAssetBalanceItem(
	String asset,
	BigDecimal available,
	BigDecimal frozen,
	BigDecimal total
) {
}
