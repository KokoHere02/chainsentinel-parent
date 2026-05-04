package com.chainsentinel.core.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradePositionSnapshotView(
	Long id,
	Long accountId,
	String symbol,
	String baseAsset,
	String quoteAsset,
	BigDecimal quantity,
	BigDecimal avgCost,
	BigDecimal marketPrice,
	BigDecimal marketValue,
	BigDecimal unrealizedPnl,
	BigDecimal unrealizedPnlRatio,
	String source,
	Instant snapshotTime
) {
}
