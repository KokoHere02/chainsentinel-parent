package com.chainsentinel.infra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

@ExtendWith(MockitoExtension.class)
class RuntimeConfigRedisInvalidationSubscriberTest {

	@Mock
	private DbPriceProviderRuntimeConfig runtimeConfig;

	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void shouldRecordInvalidationLagAndConsumeCount() throws Exception {
		RuntimeConfigCacheProperties properties = new RuntimeConfigCacheProperties();
		Clock clock = Clock.fixed(Instant.ofEpochMilli(2_000L), ZoneOffset.UTC);
		RuntimeConfigRedisInvalidationSubscriber subscriber = new RuntimeConfigRedisInvalidationSubscriber(
			runtimeConfig,
			properties,
			meterRegistry,
			objectMapper,
			clock
		);
		RuntimeConfigInvalidationMessage payload = new RuntimeConfigInvalidationMessage(1_750L);
		Message message = new DefaultMessage(
			properties.getInvalidationChannel().getBytes(StandardCharsets.UTF_8),
			objectMapper.writeValueAsBytes(payload)
		);

		subscriber.onMessage(message, null);

		verify(runtimeConfig).handleRemoteInvalidation();
		Counter counter = meterRegistry.find("price_runtime_config_invalidation_consume_total").counter();
		assertNotNull(counter);
		assertEquals(1.0d, counter.count());
		Timer timer = meterRegistry.find("price_runtime_config_invalidation_lag").timer();
		assertNotNull(timer);
		assertEquals(1L, timer.count());
	}

	@Test
	void shouldCountConsumeErrorsForInvalidPayload() {
		RuntimeConfigCacheProperties properties = new RuntimeConfigCacheProperties();
		RuntimeConfigRedisInvalidationSubscriber subscriber = new RuntimeConfigRedisInvalidationSubscriber(
			runtimeConfig,
			properties,
			meterRegistry,
			objectMapper,
			Clock.systemUTC()
		);
		Message message = new DefaultMessage(
			properties.getInvalidationChannel().getBytes(StandardCharsets.UTF_8),
			"bad-json".getBytes(StandardCharsets.UTF_8)
		);

		subscriber.onMessage(message, null);

		verifyNoInteractions(runtimeConfig);
		Counter counter = meterRegistry.find("price_runtime_config_cache_error_total")
			.tags("layer", "l2", "op", "consume")
			.counter();
		assertNotNull(counter);
		assertEquals(1.0d, counter.count());
	}
}
