package com.chainsentinel.core.service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceSnapshotUpsertCommand(
  Long assetId,
  String providerName,
  String instType,
  String instId,
  String quoteSymbol,
  BigDecimal price,
  LocalDateTime bucketTs,
  LocalDateTime quotedAt
) {
}