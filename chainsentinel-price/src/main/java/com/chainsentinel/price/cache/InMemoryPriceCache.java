package com.chainsentinel.price.cache;

import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryPriceCache implements PriceCache {

	private final Map<String, PriceQuote> cache = new ConcurrentHashMap<>();

	@Override
	public Optional<PriceQuote> get(PriceQuery query) {
		return Optional.ofNullable(cache.get(cacheKey(query)));
	}

	@Override
	public void put(PriceQuery query, PriceQuote quote) {
		cache.put(cacheKey(query), quote);
	}

	private String cacheKey(PriceQuery query) {
		return query.chain() + "|" + query.normalizedInstId();
	}
}
