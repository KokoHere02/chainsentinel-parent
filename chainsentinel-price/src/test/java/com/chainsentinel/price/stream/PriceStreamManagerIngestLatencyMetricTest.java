package com.chainsentinel.price.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.cache.PriceCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PriceStreamManagerIngestLatencyMetricTest {

	@Mock
	private PriceCache priceCache;

	@Mock
	private PriceTickBatchWriter priceTickBatchWriter;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Test
	void shouldRecordWsIngestDelayMetricOnQuoteReceived() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		PriceStreamQuote quote = new PriceStreamQuote(
			"okx_ws",
			"OFFCHAIN",
			PriceInstType.SPOT,
			"BTC",
			"USDT",
			new BigDecimal("70000"),
			System.currentTimeMillis() - 50
		);
		PriceStreamProvider provider = new OneShotProvider(quote);

		new PriceStreamManager(
			List.of(provider),
			priceCache,
			priceTickBatchWriter,
			eventPublisher,
			registry
		);

		assertEquals(1L,
			registry.get("price_ws_ingest_delay")
				.tags("provider", "okx_ws", "instType", "SPOT")
				.timer().count());
	}

	private static class OneShotProvider implements PriceStreamProvider {

		private final PriceStreamQuote quote;

		private OneShotProvider(PriceStreamQuote quote) {
			this.quote = quote;
		}

		@Override
		public String name() {
			return "oneshot";
		}

		@Override
		public boolean enabled() {
			return true;
		}

		@Override
		public boolean supports(PriceQuery query) {
			return true;
		}

		@Override
		public void start(PriceStreamSink sink) {
			sink.onQuote(quote);
		}

		@Override
		public void subscribe(List<PriceQuery> queries) {
		}

		@Override
		public void stop() {
		}
	}
}