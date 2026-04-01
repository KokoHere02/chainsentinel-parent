package com.chainsentinel.price.api.dto;

import java.math.BigDecimal;

public record PriceQuote(
  String baseSymbol,
  String quoteSymbol,
  BigDecimal price,
  long ts,
  String source,
  boolean stale
) {
}
