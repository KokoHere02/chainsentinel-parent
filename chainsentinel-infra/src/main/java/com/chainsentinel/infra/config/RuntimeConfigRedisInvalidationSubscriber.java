package com.chainsentinel.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class RuntimeConfigRedisInvalidationSubscriber implements MessageListener {

	private static final Logger log = LoggerFactory.getLogger(RuntimeConfigRedisInvalidationSubscriber.class);

	private final DbPriceProviderRuntimeConfig runtimeConfig;
	private final RuntimeConfigCacheProperties properties;
	private final MeterRegistry meterRegistry;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	@Autowired
	public RuntimeConfigRedisInvalidationSubscriber(
		DbPriceProviderRuntimeConfig runtimeConfig,
		RuntimeConfigCacheProperties properties,
		MeterRegistry meterRegistry,
		ObjectMapper objectMapper,
		Clock clock
	) {
		this.runtimeConfig = runtimeConfig;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
		if (!properties.getInvalidationChannel().equals(channel)) {
			return;
		}
		try {
			RuntimeConfigInvalidationMessage invalidationMessage = objectMapper.readValue(
				message.getBody(),
				RuntimeConfigInvalidationMessage.class
			);
			long lagMs = Math.max(0L, clock.millis() - invalidationMessage.publishedAtEpochMs());
			runtimeConfig.handleRemoteInvalidation();
			meterRegistry.counter("price_runtime_config_invalidation_consume_total").increment();
			meterRegistry.timer("price_runtime_config_invalidation_lag").record(java.time.Duration.ofMillis(lagMs));
			log.info("price.runtime.config.cache.invalidation.consumed channel={} lagMs={}", channel, lagMs);
		} catch (Exception ex) {
			log.warn("price.runtime.config.cache.invalidation.consume_failed channel={} error={}", channel, ex.getMessage(), ex);
			meterRegistry.counter("price_runtime_config_cache_error_total", "layer", "l2", "op", "consume").increment();
		}
	}
}
