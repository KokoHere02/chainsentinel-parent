package com.chainsentinel.infra.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@EnableConfigurationProperties(RuntimeConfigCacheProperties.class)
public class PriceRuntimeConfigRedisConfiguration {

	private static final Logger log = LoggerFactory.getLogger(PriceRuntimeConfigRedisConfiguration.class);

	@Bean
	Clock runtimeConfigClock() {
		return Clock.systemUTC();
	}

	@Bean
	@ConditionalOnBean(RedisConnectionFactory.class)
	@ConditionalOnProperty(prefix = "chainsentinel.cache.runtime-config", name = "l2-enabled", havingValue = "true")
	RedisMessageListenerContainer runtimeConfigRedisMessageListenerContainer(
		RedisConnectionFactory redisConnectionFactory,
		RuntimeConfigRedisInvalidationSubscriber subscriber,
		RuntimeConfigCacheProperties properties,
		MeterRegistry meterRegistry
	) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(redisConnectionFactory);
		container.addMessageListener(subscriber, new ChannelTopic(properties.getInvalidationChannel()));
		container.setErrorHandler(ex -> {
			log.warn("price.runtime.config.redis.listener_failed error={}", ex.getMessage(), ex);
			meterRegistry.counter("price_runtime_config_cache_error_total", "layer", "l2", "op", "sub").increment();
		});
		return container;
	}
}
