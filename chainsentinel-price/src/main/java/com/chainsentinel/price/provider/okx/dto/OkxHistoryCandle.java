package com.chainsentinel.price.provider.okx.dto;

import java.math.BigDecimal;

public record OkxHistoryCandle(long ts, BigDecimal closePrice) {
}