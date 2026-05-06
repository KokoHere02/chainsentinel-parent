package com.chainsentinel.price.stream;

import com.chainsentinel.price.api.dto.PriceOrderBook;

public record PriceOrderBookEvent(PriceOrderBook orderBook) {
}
