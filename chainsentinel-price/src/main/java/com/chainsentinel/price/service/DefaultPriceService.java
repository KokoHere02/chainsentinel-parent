package com.chainsentinel.price.service;

import com.chainsentinel.price.api.PriceService;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import com.chainsentinel.price.cache.PriceCache;
import com.chainsentinel.price.config.PriceProperties;
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
    private final PriceProperties priceProperties;

    public DefaultPriceService(ProviderRouter providerRouter, PriceCache priceCache, PriceProperties priceProperties) {
        this.providerRouter = providerRouter;
        this.priceCache = priceCache;
        this.priceProperties = priceProperties;
    }

    @Override
    public Optional<PriceQuote> getQuote(PriceQuery query) {
        Optional<PriceQuote> quote = providerRouter.getQuote(query, normalizedPriority());
        if (quote.isPresent()) {
            priceCache.put(query, quote.get());
            return quote;
        }

        Optional<PriceQuote> cached = priceCache.get(query).map(q -> new PriceQuote(
                q.baseSymbol(),
                q.quoteSymbol(),
                q.price(),
                q.ts(),
                q.source(),
                true
        ));
        if (cached.isPresent()) {
            log.warn("price.fetch.fallback cache=true instId={}", query.normalizedInstId());
        }
        return cached;
    }

    private Map<String, Integer> normalizedPriority() {
        Map<String, Integer> raw = priceProperties.getProviderPriority();
        if (raw == null || raw.isEmpty()) {
            Map<String, Integer> defaults = new LinkedHashMap<>();
            defaults.put("okx", 1);
            return defaults;
        }
        return raw;
    }
}
