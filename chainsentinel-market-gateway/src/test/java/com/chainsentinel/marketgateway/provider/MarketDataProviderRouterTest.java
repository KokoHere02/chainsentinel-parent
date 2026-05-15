package com.chainsentinel.marketgateway.provider;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.chainsentinel.marketgateway.api.MarketGatewayException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketDataProviderRouterTest {

	@Mock
	private MarketDataProvider provider;

	@Mock
	private MarketDataProvider noopProvider;

	@Test
	void shouldResolveSupportingProvider() {
		when(provider.supportsProvider("noop")).thenReturn(true);

		MarketDataProviderRouter router = new MarketDataProviderRouter(List.of(provider));

		assertSame(provider, router.resolve("noop"));
	}

	@Test
	void shouldResolveDefaultUpProviderBeforeNoop() {
		when(noopProvider.descriptor()).thenReturn(new MarketDataProviderDescriptor(
			"noop",
			MarketDataProviderStatus.DEGRADED,
			List.of(MarketDataCapability.QUOTE),
			"noop"
		));
		when(provider.descriptor()).thenReturn(new MarketDataProviderDescriptor(
			"okx",
			MarketDataProviderStatus.UP,
			List.of(MarketDataCapability.QUOTE),
			"okx"
		));

		MarketDataProviderRouter router = new MarketDataProviderRouter(List.of(noopProvider, provider));

		assertSame(provider, router.resolve(null));
	}

	@Test
	void shouldRejectUnsupportedProvider() {
		MarketDataProviderRouter router = new MarketDataProviderRouter(List.of(provider));

		assertThrows(MarketGatewayException.class, () -> router.resolve("akshare"));
	}

	@Test
	void shouldReturnProviderDescriptors() {
		when(provider.descriptor()).thenReturn(new MarketDataProviderDescriptor(
			"noop",
			MarketDataProviderStatus.DEGRADED,
			List.of(MarketDataCapability.QUOTE),
			"test"
		));

		MarketDataProviderRouter router = new MarketDataProviderRouter(List.of(provider));

		assertEquals(1, router.descriptors().size());
		assertEquals("noop", router.descriptors().get(0).provider());
	}
}
