package com.chainsentinel.core.service.dto;

import java.time.Instant;

public record TradeAccountAssetSyncView(
	Long accountId,
	int balanceCount,
	int positionCount,
	Instant snapshotTime
) {
}
