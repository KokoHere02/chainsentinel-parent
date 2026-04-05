package com.chainsentinel.price.provider;

import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProviderRouter {

	private static final Logger log = LoggerFactory.getLogger(ProviderRouter.class);

	private final List<PriceProvider> providers;

	public ProviderRouter(List<PriceProvider> providers) {
		this.providers = providers;
	}

	public Optional<PriceQuote> getQuote(PriceQuery query, Map<String, Integer> providerPriority) {
		Map<String, Integer> priorities = providerPriority == null ? Map.of() : providerPriority;
		List<PriceProvider> candidates = new ArrayList<>();
		for (PriceProvider provider : providers) {
			if (provider.supports(query)) {
				candidates.add(provider);
			}
		}
		if (candidates.isEmpty()) {
			log.warn("price.fetch.no_candidate instId={} providers={} priorityKeys={}",
				query == null ? null : query.normalizedInstId(),
				providers.stream().map(PriceProvider::name).toList(),
				priorities.keySet());
			return Optional.empty();
		}
		candidates.sort(Comparator.comparingInt(p -> priorities.getOrDefault(p.name(), Integer.MAX_VALUE)));

		for (PriceProvider provider : candidates) {
			try {
				Optional<PriceQuote> quote = provider.getQuote(query);
				if (quote.isPresent()) {
					return quote;
				}
			} catch (Exception ex) {
				log.warn("price.fetch.provider_error provider={} instId={} category={} error={}",
					provider.name(),
					query == null ? null : query.normalizedInstId(),
					classifyErrorCategory(ex),
					ex.getMessage());
			}
		}
		return Optional.empty();
	}

	private String classifyErrorCategory(Throwable ex) {
		Throwable root = ex;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		if (root instanceof SocketTimeoutException
			|| root instanceof ConnectException
			|| root instanceof UnknownHostException
			|| root instanceof NoRouteToHostException
			|| root instanceof SocketException
			|| root instanceof SSLException) {
			return "network";
		}
		if (root instanceof IllegalArgumentException || root instanceof IllegalStateException) {
			return "config_or_request";
		}
		return "other";
	}
}
