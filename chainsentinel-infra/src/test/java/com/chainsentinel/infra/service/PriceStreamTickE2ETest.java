package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.config.PriceTickIngestProperties;
import com.chainsentinel.infra.entity.PriceTickEntity;
import com.chainsentinel.infra.repository.PriceTickRepository;
import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import com.chainsentinel.price.cache.InMemoryPriceCache;
import com.chainsentinel.price.stream.PriceStreamManager;
import com.chainsentinel.price.stream.PriceStreamProvider;
import com.chainsentinel.price.stream.PriceStreamQuote;
import com.chainsentinel.price.stream.PriceStreamSink;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PriceStreamTickE2ETest {

	@Mock
	private PriceTickRepository priceTickRepository;

	@Test
	void shouldHitPriceCacheAndPersistTickWhenWsQuoteArrives() {
		PriceTickIngestProperties properties = new PriceTickIngestProperties();
		properties.setEnabled(true);
		properties.setBatchSize(50);
		properties.setQueueCapacity(500);

		DbPriceTickBatchWriter batchWriter = new DbPriceTickBatchWriter(
			priceTickRepository,
			properties,
			new SimpleMeterRegistry()
		);
		when(priceTickRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

		InMemoryPriceCache cache = new InMemoryPriceCache();
		PriceStreamQuote emittedQuote = new PriceStreamQuote(
			"okx_ws",
			"OFFCHAIN",
			PriceInstType.SPOT,
			"BTC",
			"USDT",
			new BigDecimal("70500.12"),
			1700000000000L
		);
		PriceStreamProvider provider = new OneShotProvider(emittedQuote);
		ApplicationEventPublisher eventPublisher = event -> {
		};
		new PriceStreamManager(List.of(provider), cache, batchWriter, eventPublisher, new SimpleMeterRegistry());

		PriceQuery query = new PriceQuery("OFFCHAIN", PriceInstType.SPOT, "BTC", "USDT", null);
		PriceQuote quote = cache.get(query).orElseThrow();
		assertEquals(new BigDecimal("70500.12"), quote.price());
		assertTrue(cache.get(query).isPresent());

		batchWriter.flushNow();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<PriceTickEntity>> captor = ArgumentCaptor.forClass(List.class);
		verify(priceTickRepository, times(1)).saveAll(captor.capture());
		List<PriceTickEntity> saved = captor.getValue();
		assertEquals(1, saved.size());
		assertEquals("BTC-USDT", saved.get(0).getInstId());
		assertEquals(new BigDecimal("70500.12"), saved.get(0).getPrice());
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
