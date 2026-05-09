package com.chainsentinel.price.provider.okx.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.mock;

class OkxWsPriceStreamProviderMetricsTest {

	@Test
	void shouldRecordDisconnectAndRecoveryMetrics() throws Exception {
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		OkxWsPriceStreamProvider provider = new OkxWsPriceStreamProvider(
			mock(PriceProviderRuntimeConfig.class),
			mock(OkxWsMessageParser.class),
			meterRegistry,
			new OkxWsQuoteGuardProperties()
		);

		Method markDisconnected = OkxWsPriceStreamProvider.class.getDeclaredMethod("markDisconnected", String.class);
		markDisconnected.setAccessible(true);
		markDisconnected.invoke(provider, "error");
		assertEquals(1.0,
			meterRegistry.get("price_ws_disconnect_total")
				.tags("provider", "okx_ws", "type", "error")
				.counter()
				.count());

		AtomicLong disconnectedAt = (AtomicLong) ReflectionTestUtils.getField(provider, "lastDisconnectedAtMs");
		disconnectedAt.set(System.currentTimeMillis() - 1000L);
		Method recordRecoveryDurationIfNeeded = OkxWsPriceStreamProvider.class.getDeclaredMethod("recordRecoveryDurationIfNeeded");
		recordRecoveryDurationIfNeeded.setAccessible(true);
		recordRecoveryDurationIfNeeded.invoke(provider);
		assertEquals(1L,
			meterRegistry.get("price_ws_recovery_duration")
				.tags("provider", "okx_ws")
				.timer()
				.count());
	}
}
