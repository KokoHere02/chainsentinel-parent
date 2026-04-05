package com.chainsentinel.price.cache;

import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import java.util.Optional;

public interface PriceCache {

	Optional<PriceQuote> get(PriceQuery query);

	void put(PriceQuery query, PriceQuote quote);
}
