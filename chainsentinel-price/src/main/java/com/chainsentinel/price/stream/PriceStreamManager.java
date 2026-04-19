package com.chainsentinel.price.stream;

import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import com.chainsentinel.price.cache.PriceCache;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class PriceStreamManager {

	private static final Logger log = LoggerFactory.getLogger(PriceStreamManager.class);
	private static final String METRIC_WS_INGEST_DELAY = "price_ws_ingest_delay";

	private final List<PriceStreamProvider> providers;
	private final PriceCache priceCache;
	private final PriceTickBatchWriter priceTickBatchWriter;
	private final ApplicationEventPublisher eventPublisher;
	private final MeterRegistry meterRegistry;
	private final Map<String, String> providerSubscriptionFingerprints = new ConcurrentHashMap<>();

	public PriceStreamManager(
		List<PriceStreamProvider> providers,
		PriceCache priceCache,
		PriceTickBatchWriter priceTickBatchWriter,
		ApplicationEventPublisher eventPublisher,
		MeterRegistry meterRegistry
	) {
		this.providers = providers;
		this.priceCache = priceCache;
		this.priceTickBatchWriter = priceTickBatchWriter;
		this.eventPublisher = eventPublisher;
		this.meterRegistry = meterRegistry;
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
				providerSubscriptionFingerprints.remove(provider.name());
				continue;
			}
			String providerName = provider.name();
			String fingerprint = buildSubscriptionFingerprint(supported);
			String previous = providerSubscriptionFingerprints.get(providerName);
			if (fingerprint.equals(previous)) {
				log.debug("price.ws.subscribe.skip provider={} reason=unchanged count={}", providerName, supported.size());
				continue;
			}
			provider.subscribe(supported);
			providerSubscriptionFingerprints.put(providerName, fingerprint);
			log.info("price.ws.subscribe provider={} count={} changed={}", providerName, supported.size(), previous != null);
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

	private String buildSubscriptionFingerprint(List<PriceQuery> queries) {
		List<String> normalized = new ArrayList<>();
		for (PriceQuery query : queries) {
			normalized.add(normalizeQuery(query));
		}
		normalized.sort(Comparator.naturalOrder());
		StringBuilder sb = new StringBuilder();
		String previous = null;
		for (String item : normalized) {
			if (Objects.equals(previous, item)) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append(';');
			}
			sb.append(item);
			previous = item;
		}
		return sb.toString();
	}

	private String normalizeQuery(PriceQuery query) {
		if (query == null) {
			return "";
		}
		String chain = normalizeUpper(query.chain());
		String instType = query.instType() == null ? "" : query.instType().name();
		String symbol = normalizeUpper(query.symbol());
		String quoteSymbol = normalizeUpper(query.quoteSymbol());
		String tokenAddress = normalizeLower(query.tokenAddress());
		return chain + '|' + instType + '|' + symbol + '|' + quoteSymbol + '|' + tokenAddress;
	}

	private String normalizeUpper(String input) {
		if (input == null) {
			return "";
		}
		return input.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeLower(String input) {
		if (input == null) {
			return "";
		}
		return input.trim().toLowerCase(Locale.ROOT);
	}

	private void handleQuote(PriceStreamQuote quote) {
		if (quote == null) {
			return;
		}
		recordIngestDelay(quote);
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
		try {
			priceTickBatchWriter.enqueue(quote);
		} catch (Exception ex) {
			log.warn("price.ws.tick.enqueue.failed provider={} instId={} error={}",
				quote.providerName(), quote.instId(), ex.getMessage());
		}
		eventPublisher.publishEvent(new PriceStreamQuoteEvent(quote));
		log.debug("price.ws.quote provider={} instId={} price={}", quote.providerName(), quote.instId(), quote.price());
	}

	private void recordIngestDelay(PriceStreamQuote quote) {
		long quoteTs = quote.ts();
		if (quoteTs <= 0L) {
			return;
		}
		long delayMs = Math.max(0L, System.currentTimeMillis() - quoteTs);
		String provider = normalizeMetricTag(quote.providerName());
		String instType = quote.instType() == null ? "UNKNOWN" : quote.instType().name();
		meterRegistry.timer(METRIC_WS_INGEST_DELAY, "provider", provider, "instType", instType)
			.record(delayMs, TimeUnit.MILLISECONDS);
	}

	private String normalizeMetricTag(String value) {
		if (value == null || value.isBlank()) {
			return "unknown";
		}
		return value;
	}
}