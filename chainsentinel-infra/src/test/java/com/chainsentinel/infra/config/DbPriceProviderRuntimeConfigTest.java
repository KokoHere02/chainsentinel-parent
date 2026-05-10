package com.chainsentinel.infra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.entity.PriceProviderConfigEntity;
import com.chainsentinel.infra.repository.PriceProviderConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class DbPriceProviderRuntimeConfigTest {

	@Mock
	private PriceProviderConfigRepository priceProviderConfigRepository;

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void shouldFilterByEnabledWhenCheckProviderEnabled() {
		when(priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc())
			.thenReturn(List.of(provider("binance", "https://api.binance.com", 2, 1500)));

		DbPriceProviderRuntimeConfig config = newConfig(false);

		assertFalse(config.providerEnabled("okx"));
		assertTrue(config.providerEnabled("  BINANCE  "));
		assertFalse(config.providerEnabled("   "));
	}

	@Test
	void shouldKeepPriorityOrderFromRepositoryResult() {
		List<PriceProviderConfigEntity> enabledProviders = List.of(
			provider("okx", "https://www.okx.com", 1, 1200),
			provider("binance", "https://api.binance.com", 2, 1500)
		);
		when(priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc())
			.thenReturn(enabledProviders);

		DbPriceProviderRuntimeConfig config = newConfig(false);
		Map<String, Integer> priorities = config.providerPriority();

		assertIterableEquals(List.of("okx", "binance"), new ArrayList<>(priorities.keySet()));
		assertEquals(1, priorities.get("okx"));
		assertEquals(2, priorities.get("binance"));
	}

	@Test
	void shouldReadBaseUrlAndTimeoutFromSnapshotOrFallbackToDefault() {
		when(priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc())
			.thenReturn(List.of(provider("okx", " https://okx.com ", 1, 2500)));

		DbPriceProviderRuntimeConfig config = newConfig(false);

		assertEquals("https://okx.com", config.providerBaseUrl("okx", "https://www.okx.com"));
		assertEquals(2500, config.providerTimeoutMs("okx", 1500));
		assertEquals("https://default.example", config.providerBaseUrl("missing", "https://default.example"));
		assertEquals(1800, config.providerTimeoutMs("missing", 1800));
	}

	@Test
	void shouldFallbackToDefaultWhenProviderConfigIsInvalid() {
		PriceProviderConfigEntity invalid = provider("okx", "   ", 0, 0);
		when(priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc())
			.thenReturn(List.of(invalid));

		DbPriceProviderRuntimeConfig config = newConfig(false);

		Map<String, Integer> priorities = config.providerPriority();
		assertEquals(Integer.MAX_VALUE, priorities.get("okx"));
		assertEquals("https://default.example", config.providerBaseUrl("okx", "https://default.example"));
		assertEquals(1500, config.providerTimeoutMs("okx", 1500));
	}

	@Test
	void shouldReturnSafeDefaultsWhenRepositoryThrows() {
		doThrow(new RuntimeException("db down"))
			.when(priceProviderConfigRepository).findByEnabledTrueOrderByPriorityAscIdAsc();

		DbPriceProviderRuntimeConfig config = newConfig(false);

		assertTrue(config.providerPriority().isEmpty());
		assertFalse(config.providerEnabled("okx"));
		assertEquals("https://default.example", config.providerBaseUrl("okx", "https://default.example"));
		assertEquals(1500, config.providerTimeoutMs("okx", 1500));
	}

	@Test
	void shouldReadSnapshotFromRedisBeforeDb() throws Exception {
		RuntimeConfigSnapshot snapshot = new RuntimeConfigSnapshot(
			Map.of("okx", new RuntimeConfigSnapshot.ProviderConfigSnapshot("okx", "https://redis.okx.com", 1, 2100)),
			Map.of("okx", 1)
		);
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get("cs:price:runtime:snapshot"))
			.thenReturn(objectMapper.writeValueAsString(snapshot));

		DbPriceProviderRuntimeConfig config = newConfig(true);

		assertEquals("https://redis.okx.com", config.providerBaseUrl("okx", "https://default.example"));
		verify(priceProviderConfigRepository, never()).findByEnabledTrueOrderByPriorityAscIdAsc();
	}

	@Test
	void shouldWriteSnapshotToRedisAfterDbLoad() {
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		when(priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc())
			.thenReturn(List.of(provider("okx", "https://www.okx.com", 1, 1200)));

		DbPriceProviderRuntimeConfig config = newConfig(true);

		assertTrue(config.providerEnabled("okx"));
		verify(valueOperations).set(eq("cs:price:runtime:snapshot"), any(String.class), eq(Duration.ofSeconds(90)));
	}

	@Test
	void shouldApplyTtlJitterWhenWriteSnapshotToRedisAfterDbLoad() {
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		when(priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc())
			.thenReturn(List.of(provider("okx", "https://www.okx.com", 1, 1200)));

		RuntimeConfigCacheProperties properties = new RuntimeConfigCacheProperties();
		properties.setL2Enabled(true);
		properties.setL2Ttl(Duration.ofSeconds(90));
		properties.setL2TtlJitterRatio(0.1d);
		DbPriceProviderRuntimeConfig config = new DbPriceProviderRuntimeConfig(
			priceProviderConfigRepository,
			meterRegistry,
			objectMapper,
			properties,
			beanProviderOf(stringRedisTemplate)
		);

		assertTrue(config.providerEnabled("okx"));
		verify(valueOperations).set(
			eq("cs:price:runtime:snapshot"),
			any(String.class),
			argThat(ttl -> ttl.compareTo(Duration.ofSeconds(90)) >= 0 && ttl.compareTo(Duration.ofSeconds(99)) <= 0)
		);
	}

	@Test
	void shouldRecordRebuildDurationOnDbLoad() {
		when(priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc())
			.thenReturn(List.of(provider("okx", "https://www.okx.com", 1, 1200)));

		DbPriceProviderRuntimeConfig config = newConfig(false);

		assertTrue(config.providerEnabled("okx"));
		Timer timer = meterRegistry.find("price_runtime_config_cache_rebuild_duration")
			.tags("result", "success")
			.timer();
		assertNotNull(timer);
		assertEquals(1L, timer.count());
	}

	@Test
	void shouldUseSingleFlightForConcurrentDbLoads() throws Exception {
		AtomicInteger loadCount = new AtomicInteger();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc()).thenAnswer(invocation -> {
			loadCount.incrementAndGet();
			entered.countDown();
			assertTrue(release.await(2, TimeUnit.SECONDS));
			return List.of(provider("okx", "https://www.okx.com", 1, 1200));
		});

		DbPriceProviderRuntimeConfig config = newConfig(false);
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		try {
			Future<Boolean> first = executorService.submit(() -> config.providerEnabled("okx"));
			assertTrue(entered.await(2, TimeUnit.SECONDS));
			Future<Boolean> second = executorService.submit(() -> config.providerEnabled("okx"));
			release.countDown();

			assertTrue(first.get(2, TimeUnit.SECONDS));
			assertTrue(second.get(2, TimeUnit.SECONDS));
			assertEquals(1, loadCount.get());
		} finally {
			executorService.shutdownNow();
		}
	}

	@Test
	void shouldRetryRedisDeleteOnRefresh() {
		when(stringRedisTemplate.delete("cs:price:runtime:snapshot"))
			.thenThrow(new RuntimeException("redis busy"))
			.thenReturn(Boolean.TRUE);
		RuntimeConfigCacheProperties properties = new RuntimeConfigCacheProperties();
		properties.setL2Enabled(true);
		properties.setL2DeleteRetryMax(2);
		properties.setL2DeleteRetryBackoff(Duration.ZERO);
		DbPriceProviderRuntimeConfig config = new DbPriceProviderRuntimeConfig(
			priceProviderConfigRepository,
			meterRegistry,
			objectMapper,
			properties,
			beanProviderOf(stringRedisTemplate)
		);

		config.refreshCache();

		verify(stringRedisTemplate, times(2)).delete("cs:price:runtime:snapshot");
	}

	@Test
	void shouldEvictRedisAndPublishInvalidationOnRefresh() throws Exception {
		DbPriceProviderRuntimeConfig config = newConfig(true);

		config.refreshCache();

		verify(stringRedisTemplate).delete("cs:price:runtime:snapshot");
		org.mockito.ArgumentCaptor<String> payloadCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(stringRedisTemplate).convertAndSend(eq("cs:cache:invalidate:price-runtime-config"), payloadCaptor.capture());
		RuntimeConfigInvalidationMessage message =
			objectMapper.readValue(payloadCaptor.getValue(), RuntimeConfigInvalidationMessage.class);
		assertTrue(message.publishedAtEpochMs() > 0);
	}

	private DbPriceProviderRuntimeConfig newConfig(boolean l2Enabled) {
		RuntimeConfigCacheProperties properties = new RuntimeConfigCacheProperties();
		properties.setL2Enabled(l2Enabled);
		properties.setL1Ttl(Duration.ofSeconds(10));
		properties.setL2Ttl(Duration.ofSeconds(90));
		properties.setL2TtlJitterRatio(0d);
		return new DbPriceProviderRuntimeConfig(
			priceProviderConfigRepository,
			meterRegistry,
			objectMapper,
			properties,
			l2Enabled ? beanProviderOf(stringRedisTemplate) : beanProviderOf(null)
		);
	}

	private org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> beanProviderOf(
		StringRedisTemplate template
	) {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		if (template != null) {
			beanFactory.registerSingleton("stringRedisTemplate", template);
		}
		return beanFactory.getBeanProvider(StringRedisTemplate.class);
	}

	private PriceProviderConfigEntity provider(String name, String baseUrl, Integer priority, Integer timeoutMs) {
		PriceProviderConfigEntity entity = new PriceProviderConfigEntity();
		entity.setProviderName(name);
		entity.setBaseUrl(baseUrl);
		entity.setPriority(priority);
		entity.setTimeoutMs(timeoutMs);
		entity.setEnabled(true);
		return entity;
	}
}
