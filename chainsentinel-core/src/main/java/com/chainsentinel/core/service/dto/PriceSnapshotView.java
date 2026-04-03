package com.chainsentinel.core.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record PriceSnapshotView(
  Long id,
  Long assetId,
  String providerName,
  String instType,
  String instId,
  String quoteSymbol,
  BigDecimal price,
  LocalDateTime bucketTs,
  LocalDateTime quotedAt,
  Instant fetchedAt
) {
}