package com.chainsentinel.price.stream;

import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import com.chainsentinel.price.cache.PriceCache;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PriceStreamManager {

	private static final Logger log = LoggerFactory.getLogger(PriceStreamManager.class);

	private final List<PriceStreamProvider> providers;
	private final PriceCache priceCache;

	public PriceStreamManager(List<PriceStreamProvider> providers, PriceCache priceCache) {
		this.providers = providers;
		this.priceCache = priceCache;
		startEnabledProviders();
	}

	public void refreshSubscriptions(List<PriceQuery> queries) {
		if (queries == null || queries.isEmpty()) {
			log.info("price.ws.refresh.skip reason=empty_queries");
			return;
		}
		for (PriceStreamProvider provider : providers) {
			if (!provider.enabled()) {
				continue;
			}
			List<PriceQuery> supported = filterSupported(provider, queries);
			if (supported.isEmpty()) {
				continue;
			}
			provider.subscribe(supported);
			log.info("price.ws.subscribe provider={} count={}", provider.name(), supported.size());
		}
	}

	private void startEnabledProviders() {
		for (PriceStreamProvider provider : providers) {
			if (!provider.enabled()) {
				log.info("price.ws.provider.disabled provider={}", provider.name());
				continue;
			}
			provider.start(this::handleQuote);
			log.info("price.ws.provider.started provider={}", provider.name());
		}
	}

	private List<PriceQuery> filterSupported(PriceStreamProvider provider, List<PriceQuery> queries) {
		List<PriceQuery> supported = new ArrayList<>();
		for (PriceQuery query : queries) {
			if (provider.supports(query)) {
				supported.add(query);
			}
		}
		return supported;
	}

	private void handleQuote(PriceStreamQuote quote) {
		PriceQuery query = new PriceQuery(
			quote.chain(),
			quote.instType(),
			quote.baseSymbol(),
			quote.quoteSymbol(),
			null
		);
		PriceQuote cacheQuote = new PriceQuote(
			quote.baseSymbol(),
			quote.quoteSymbol(),
			quote.price(),
			quote.ts(),
			quote.providerName(),
			false
		);
		priceCache.put(query, cacheQuote);
		log.debug("price.ws.quote provider={} instId={} price={}", quote.providerName(), quote.instId(), quote.price());
	}
}