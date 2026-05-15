package com.chainsentinel.price.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class PublicMarketDataClientRouterTest {

	@Mock
	private PublicMarketDataClient okxClient;

	@Test
	void shouldResolveFirstSupportingClient() {
		when(okxClient.supportsProvider("okx")).thenReturn(true);

		PublicMarketDataClientRouter router = new PublicMarketDataClientRouter(List.of(okxClient));

		assertSame(okxClient, router.resolve("okx"));
	}

	@Test
	void shouldRejectUnsupportedProvider() {
		PublicMarketDataClientRouter router = new PublicMarketDataClientRouter(List.of(okxClient));

		assertThrows(IllegalArgumentException.class, () -> router.resolve("binance"));
	}
}
