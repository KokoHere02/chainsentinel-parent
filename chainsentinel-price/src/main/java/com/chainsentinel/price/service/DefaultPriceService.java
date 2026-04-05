package com.chainsentinel.price.service;

import com.chainsentinel.price.api.PriceService;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import com.chainsentinel.price.cache.PriceCache;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import com.chainsentinel.price.provider.ProviderRouter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultPriceService implements PriceService {

	private static final Logger log = LoggerFactory.getLogger(DefaultPriceService.class);

	private final ProviderRouter providerRouter;
	private final PriceCache priceCache;
	private final PriceProviderRuntimeConfig runtimeConfig;

	public DefaultPriceService(ProviderRouter providerRouter, PriceCache priceCache, PriceProviderRuntimeConfig runtimeConfig) {
		this.providerRouter = providerRouter;
		this.priceCache = priceCache;
		this.runtimeConfig = runtimeConfig;
	}

	@Override
	public Optional<PriceQuote> getQuote(PriceQuery query) {
		Map<String, Integer> runtimePriority = runtimeConfig.providerPriority();
		boolean runtimeConfigEmpty = runtimePriority == null || runtimePriority.isEmpty();
		Map<String, Integer> priorities = normalizedPriority(runtimePriority);
		Optional<PriceQuote> quote = providerRouter.getQuote(query, priorities);
		if (quote.isPresent()) {
			priceCache.put(query, quote.get());
			return quote;
		}

		String degradeReason = runtimeConfigEmpty ? "runtime_config_empty" : "provider_fetch_failed";
		Optional<PriceQuote> cached = priceCache.get(query).map(q -> new PriceQuote(
			q.baseSymbol(),
			q.quoteSymbol(),
			q.price(),
			q.ts(),
			q.source(),
			true
		));
		if (cached.isPresent()) {
			log.warn("price.fetch.fallback cache=true reason={} instId={}", degradeReason, query.normalizedInstId());
			return cached;
		}
		log.warn("price.fetch.fallback cache=false reason={} instId={}", degradeReason, query.normalizedInstId());
		return cached;
	}

	private Map<String, Integer> normalizedPriority(Map<String, Integer> raw) {
		if (raw == null || raw.isEmpty()) {
			log.warn("price.fetch.runtime_config.empty fallback=default provider=okx");
			Map<String, Integer> defaults = new LinkedHashMap<>();
			defaults.put("okx", 1);
			return defaults;
		}
		return raw;
	}
}
