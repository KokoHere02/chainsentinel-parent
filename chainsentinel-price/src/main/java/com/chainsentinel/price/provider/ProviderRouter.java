package com.chainsentinel.price.provider;

import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProviderRouter {

    private final List<PriceProvider> providers;

    public ProviderRouter(List<PriceProvider> providers) {
        this.providers = providers;
    }

    public Optional<PriceQuote> getQuote(PriceQuery query, Map<String, Integer> providerPriority) {
        List<PriceProvider> candidates = new ArrayList<>();
        for (PriceProvider provider : providers) {
            if (provider.supports(query)) {
                candidates.add(provider);
            }
        }
        candidates.sort(Comparator.comparingInt(p -> providerPriority.getOrDefault(p.name(), Integer.MAX_VALUE)));

        for (PriceProvider provider : candidates) {
            Optional<PriceQuote> quote = provider.getQuote(query);
            if (quote.isPresent()) {
                return quote;
            }
        }
        return Optional.empty();
    }
}
