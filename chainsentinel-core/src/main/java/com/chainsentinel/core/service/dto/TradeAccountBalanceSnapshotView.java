package com.chainsentinel.core.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeAccountBalanceSnapshotView(
	Long id,
	Long accountId,
	String asset,
	BigDecimal available,
	BigDecimal frozen,
	BigDecimal total,
	String source,
	Instant snapshotTime
) {
}
