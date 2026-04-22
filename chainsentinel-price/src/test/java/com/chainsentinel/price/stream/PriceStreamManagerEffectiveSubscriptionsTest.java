package com.chainsentinel.price.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.cache.PriceCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PriceStreamManagerEffectiveSubscriptionsTest {

	@Mock
	private PriceStreamProvider provider;

	@Mock
	private PriceCache priceCache;

	@Mock
	private PriceTickBatchWriter priceTickBatchWriter;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Test
	void shouldExposeCurrentEffectiveSubscriptions() {
		when(provider.enabled()).thenReturn(true);
		when(provider.name()).thenReturn("okx_ws");
		when(provider.supports(any())).thenReturn(true);

		PriceStreamManager manager = new PriceStreamManager(
			List.of(provider),
			priceCache,
			priceTickBatchWriter,
			eventPublisher,
			new SimpleMeterRegistry()
		);

		List<PriceQuery> queries = List.of(
			new PriceQuery("OFFCHAIN", PriceInstType.SPOT, "BTC", "USDT", null),
			new PriceQuery("OFFCHAIN", PriceInstType.SWAP, "ETH", "USDT", null)
		);
		manager.refreshSubscriptions(queries);

		Map<String, List<PriceQuery>> snapshot = manager.currentEffectiveSubscriptions();
		assertEquals(1, snapshot.size());
		assertEquals(2, snapshot.get("okx_ws").size());
		assertEquals("BTC-USDT", snapshot.get("okx_ws").get(0).normalizedInstId());
	}

	@Test
	void shouldClearEffectiveSubscriptionsWhenProviderDisabled() {
		when(provider.enabled()).thenReturn(true, true, false);
		when(provider.name()).thenReturn("okx_ws");
		when(provider.supports(any())).thenReturn(true);

		PriceStreamManager manager = new PriceStreamManager(
			List.of(provider),
			priceCache,
			priceTickBatchWriter,
			eventPublisher,
			new SimpleMeterRegistry()
		);

		manager.refreshSubscriptions(List.of(
			new PriceQuery("OFFCHAIN", PriceInstType.SPOT, "BTC", "USDT", null)
		));
		assertEquals(1, manager.currentEffectiveSubscriptions().size());

		manager.refreshSubscriptions(List.of(
			new PriceQuery("OFFCHAIN", PriceInstType.SPOT, "BTC", "USDT", null)
		));

		assertTrue(manager.currentEffectiveSubscriptions().isEmpty());
	}
}
