package com.chainsentinel.price.provider.okx;

import com.chainsentinel.price.api.PublicMarketDataClient;
import com.chainsentinel.price.api.PublicMarketDataClientRouter;
import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import com.chainsentinel.price.provider.PriceProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OkxPriceProvider implements PriceProvider {

	private static final Logger log = LoggerFactory.getLogger(OkxPriceProvider.class);
	private static final Set<PriceInstType> SUPPORTED_INST_TYPES = EnumSet.of(
		PriceInstType.SPOT,
		PriceInstType.MARGIN,
		PriceInstType.SWAP,
		PriceInstType.FUTURES,
		PriceInstType.OPTION
	);

	private final PriceProviderRuntimeConfig runtimeConfig;
	private final PublicMarketDataClient marketDataClient;
	private final MeterRegistry meterRegistry;

	public OkxPriceProvider(
		PriceProviderRuntimeConfig runtimeConfig,
		PublicMarketDataClientRouter marketDataClientRouter,
		MeterRegistry meterRegistry
	) {
		this.runtimeConfig = runtimeConfig;
		this.marketDataClient = marketDataClientRouter.resolve(name());
		this.meterRegistry = meterRegistry;
	}

	@Override
	public String name() {
		return "okx";
	}

	@Override
	public boolean supports(PriceQuery query) {
		return runtimeConfig.providerEnabled(name())
			&& query != null
			&& StringUtils.hasText(query.symbol())
			&& StringUtils.hasText(query.quoteSymbol())
			&& query.instType() != null
			&& SUPPORTED_INST_TYPES.contains(query.instType());
	}

	@Override
	public Optional<PriceQuote> getQuote(PriceQuery query) {
		if (!supports(query)) {
			log.warn("price.fetch.invalid_query provider=okx instType={} symbol={} quoteSymbol={}",
			query == null ? null : query.instType(),
			query == null ? null : query.symbol(),
			query == null ? null : query.quoteSymbol());
			meterRegistry.counter("price_fetch_total", "provider", name(), "status", "invalid_query").increment();
			return Optional.empty();
		}

		Optional<PriceQuote> quote = marketDataClient.getQuote(query);
		if (quote.isEmpty()) {
			meterRegistry.counter("price_fetch_total", "provider", name(), "status", "failed").increment();
			return Optional.empty();
		}

		meterRegistry.counter("price_fetch_total", "provider", name(), "status", "success").increment();
		return quote;
	}
}
