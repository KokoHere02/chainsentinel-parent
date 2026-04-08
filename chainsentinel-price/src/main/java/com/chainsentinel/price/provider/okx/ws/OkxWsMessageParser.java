package com.chainsentinel.price.provider.okx.ws;

import com.chainsentinel.price.stream.PriceStreamQuote;
import java.util.Optional;

public interface OkxWsMessageParser {

	Optional<PriceStreamQuote> parse(String payload);
}