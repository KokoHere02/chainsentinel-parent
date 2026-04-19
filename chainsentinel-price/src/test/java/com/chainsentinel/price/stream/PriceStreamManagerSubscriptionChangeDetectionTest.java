package com.chainsentinel.price.stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.cache.PriceCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PriceStreamManagerSubscriptionChangeDetectionTest {

	@Mock
	private PriceStreamProvider provider;

	@Mock
	private PriceCache priceCache;

	@Mock
	private PriceTickBatchWriter priceTickBatchWriter;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Test
	void shouldSkipResubscribeWhenSubscriptionArgsUnchanged() {
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

		List<PriceQuery> first = List.of(
			new PriceQuery("offchain", PriceInstType.SPOT, "btc", "usdt", null),
			new PriceQuery("offchain", PriceInstType.SPOT, "eth", "usdt", null)
		);
		List<PriceQuery> sameButReorderedAndCased = List.of(
			new PriceQuery("OFFCHAIN", PriceInstType.SPOT, "ETH", "USDT", null),
			new PriceQuery("offchain", PriceInstType.SPOT, "BTC", "USDT", null)
		);

		manager.refreshSubscriptions(first);
		manager.refreshSubscriptions(sameButReorderedAndCased);

		verify(provider, times(1)).subscribe(any());
	}

	@Test
	void shouldResubscribeWhenSubscriptionArgsChanged() {
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

		List<PriceQuery> first = List.of(
			new PriceQuery("OFFCHAIN", PriceInstType.SPOT, "BTC", "USDT", null)
		);
		List<PriceQuery> changed = List.of(
			new PriceQuery("OFFCHAIN", PriceInstType.SPOT, "BTC", "USDT", null),
			new PriceQuery("OFFCHAIN", PriceInstType.SPOT, "ETH", "USDT", null)
		);

		manager.refreshSubscriptions(first);
		manager.refreshSubscriptions(changed);

		verify(provider, times(2)).subscribe(any());
	}
}