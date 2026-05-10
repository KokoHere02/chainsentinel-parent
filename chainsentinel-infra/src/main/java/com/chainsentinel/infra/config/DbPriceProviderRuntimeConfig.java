package com.chainsentinel.infra.config;

import com.chainsentinel.infra.entity.PriceProviderConfigEntity;
import com.chainsentinel.infra.repository.PriceProviderConfigRepository;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Primary
public class DbPriceProviderRuntimeConfig implements PriceProviderRuntimeConfig {

	private static final Logger log = LoggerFactory.getLogger(DbPriceProviderRuntimeConfig.class);
	private static final String SNAPSHOT_CACHE_KEY = "snapshot";

	private final PriceProviderConfigRepository priceProviderConfigRepository;
	private final MeterRegistry meterRegistry;
	private final ObjectMapper objectMapper;
	private final RuntimeConfigCacheProperties cacheProperties;
	private final StringRedisTemplate stringRedisTemplate;
	private final Cache<String, RuntimeConfigSnapshot> snapshotCache;
	private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();

	private volatile String lastFallbackScene;
	private volatile String lastFallbackReason;
	private volatile String lastFallbackProvider;

	@Autowired
	public DbPriceProviderRuntimeConfig(
		PriceProviderConfigRepository priceProviderConfigRepository,
		MeterRegistry meterRegistry,
		ObjectMapper objectMapper,
		RuntimeConfigCacheProperties cacheProperties,
		ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
	) {
		this.priceProviderConfigRepository = priceProviderConfigRepository;
		this.meterRegistry = meterRegistry;
		this.objectMapper = objectMapper;
		this.cacheProperties = cacheProperties;
		this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
		this.snapshotCache = Caffeine.newBuilder()
			.expireAfterWrite(resolveL1Ttl(cacheProperties))
			.build();
	}

	@PostConstruct
	public void logStartupProviderConfig() {
		try {
			RuntimeConfigSnapshot snapshot = loadSnapshotFromDb();
			logSnapshot("startup", snapshot);
		} catch (Exception ex) {
			log.warn("price.runtime.config.startup.failed error={}", ex.getMessage());
		}
	}

	@Override
	public Map<String, Integer> providerPriority() {
		return getSnapshot().priorities();
	}

	@Override
	public boolean providerEnabled(String providerName) {
		if (!StringUtils.hasText(providerName)) {
			return false;
		}
		return getSnapshot().providers().containsKey(normalizeProviderName(providerName));
	}

	@Override
	public String providerBaseUrl(String providerName, String defaultBaseUrl) {
		if (!StringUtils.hasText(providerName)) {
			return defaultBaseUrl;
		}
		RuntimeConfigSnapshot.ProviderConfigSnapshot provider = getSnapshot().providers().get(normalizeProviderName(providerName));
		if (provider == null) {
			return defaultBaseUrl;
		}
		if (StringUtils.hasText(provider.baseUrl())) {
			return provider.baseUrl().trim();
		}
		log.warn("price.runtime.config.base_url.invalid provider={} reason=blank fallback={}", providerName, defaultBaseUrl);
		recordDbFallback("base_url", "invalid_blank", normalizeProviderName(providerName));
		return defaultBaseUrl;
	}

	@Override
	public int providerTimeoutMs(String providerName, int defaultTimeoutMs) {
		if (!StringUtils.hasText(providerName)) {
			return defaultTimeoutMs;
		}
		RuntimeConfigSnapshot.ProviderConfigSnapshot provider = getSnapshot().providers().get(normalizeProviderName(providerName));
		if (provider == null) {
			return defaultTimeoutMs;
		}
		Integer timeoutMs = provider.timeoutMs();
		if (timeoutMs != null && timeoutMs > 0) {
			return timeoutMs;
		}
		log.warn("price.runtime.config.timeout.invalid provider={} value={} fallback={}",
			providerName, timeoutMs, defaultTimeoutMs);
		recordDbFallback("timeout", "invalid_non_positive", normalizeProviderName(providerName));
		return defaultTimeoutMs;
	}

	@Override
	public void refreshCache() {
		invalidateLocalCache("local");
		evictRedisSnapshot();
		publishInvalidation();
		log.info("price.runtime.config.cache.refreshed scope=distributed l2Enabled={}", isRedisCacheEnabled());
		meterRegistry.counter("price_runtime_config_cache_refresh_total", "scope", "distributed").increment();
		logRefreshSnapshot();
	}

	void handleRemoteInvalidation() {
		invalidateLocalCache("remote");
	}

	private RuntimeConfigSnapshot getSnapshot() {
		RuntimeConfigSnapshot cached = snapshotCache.getIfPresent(SNAPSHOT_CACHE_KEY);
		if (cached != null) {
			meterRegistry.counter("price_runtime_config_cache_hit_total", "layer", "l1").increment();
			return cached;
		}

		RuntimeConfigSnapshot redisSnapshot = loadSnapshotFromRedis();
		if (redisSnapshot != null) {
			snapshotCache.put(SNAPSHOT_CACHE_KEY, redisSnapshot);
			meterRegistry.counter("price_runtime_config_cache_hit_total", "layer", "l2").increment();
			return redisSnapshot;
		}

		Object lock = loadLocks.computeIfAbsent(SNAPSHOT_CACHE_KEY, key -> new Object());
		synchronized (lock) {
			try {
				RuntimeConfigSnapshot l1Reloaded = snapshotCache.getIfPresent(SNAPSHOT_CACHE_KEY);
				if (l1Reloaded != null) {
					meterRegistry.counter("price_runtime_config_cache_hit_total", "layer", "l1").increment();
					return l1Reloaded;
				}

				RuntimeConfigSnapshot l2Reloaded = loadSnapshotFromRedis();
				if (l2Reloaded != null) {
					snapshotCache.put(SNAPSHOT_CACHE_KEY, l2Reloaded);
					meterRegistry.counter("price_runtime_config_cache_hit_total", "layer", "l2").increment();
					return l2Reloaded;
				}

				RuntimeConfigSnapshot dbSnapshot = loadSnapshotSafely();
				snapshotCache.put(SNAPSHOT_CACHE_KEY, dbSnapshot);
				writeSnapshotToRedis(dbSnapshot);
				meterRegistry.counter("price_runtime_config_cache_hit_total", "layer", "db").increment();
				return dbSnapshot;
			} finally {
				loadLocks.remove(SNAPSHOT_CACHE_KEY, lock);
			}
		}
	}

	private RuntimeConfigSnapshot loadSnapshotSafely() {
		Timer.Sample sample = Timer.start(meterRegistry);
		try {
			RuntimeConfigSnapshot snapshot = loadSnapshotFromDb();
			sample.stop(meterRegistry.timer("price_runtime_config_cache_rebuild_duration", "result", "success"));
			return snapshot;
		} catch (Exception ex) {
			sample.stop(meterRegistry.timer("price_runtime_config_cache_rebuild_duration", "result", "error"));
			log.warn("price.runtime.config.snapshot.load_failed error={}", ex.getMessage());
			recordDbFallback("snapshot", "db_error", "all");
			return RuntimeConfigSnapshot.empty();
		}
	}

	private RuntimeConfigSnapshot loadSnapshotFromDb() {
		List<PriceProviderConfigEntity> enabledProviders =
			priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc();
		Map<String, RuntimeConfigSnapshot.ProviderConfigSnapshot> providers = new LinkedHashMap<>();
		Map<String, Integer> priorities = new LinkedHashMap<>();
		for (PriceProviderConfigEntity provider : enabledProviders) {
			if (!StringUtils.hasText(provider.getProviderName())) {
				log.warn("price.runtime.config.priority.invalid providerName=blank");
				recordDbFallback("priority", "invalid_provider_name", "blank");
				continue;
			}
			String providerName = normalizeProviderName(provider.getProviderName());
			int priority = resolvePriority(provider);
			providers.put(providerName, new RuntimeConfigSnapshot.ProviderConfigSnapshot(
				providerName,
				provider.getBaseUrl(),
				provider.getPriority(),
				provider.getTimeoutMs()
			));
			priorities.put(providerName, priority);
		}
		return new RuntimeConfigSnapshot(providers, priorities);
	}

	private RuntimeConfigSnapshot loadSnapshotFromRedis() {
		if (!isRedisCacheEnabled()) {
			return null;
		}
		try {
			String payload = stringRedisTemplate.opsForValue().get(cacheProperties.snapshotKey());
			if (!StringUtils.hasText(payload)) {
				return null;
			}
			return objectMapper.readValue(payload, RuntimeConfigSnapshot.class);
		} catch (Exception ex) {
			log.warn("price.runtime.config.redis.read_failed error={}", ex.getMessage());
			meterRegistry.counter("price_runtime_config_cache_error_total", "layer", "l2", "op", "get").increment();
			return null;
		}
	}

	private void writeSnapshotToRedis(RuntimeConfigSnapshot snapshot) {
		if (!isRedisCacheEnabled()) {
			return;
		}
		try {
			Duration ttl = resolveL2TtlWithJitter();
			stringRedisTemplate.opsForValue().set(
				cacheProperties.snapshotKey(),
				objectMapper.writeValueAsString(snapshot),
				ttl
			);
		} catch (JsonProcessingException ex) {
			log.warn("price.runtime.config.redis.serialize_failed error={}", ex.getMessage());
			meterRegistry.counter("price_runtime_config_cache_error_total", "layer", "l2", "op", "serialize").increment();
		} catch (Exception ex) {
			log.warn("price.runtime.config.redis.write_failed error={}", ex.getMessage());
			meterRegistry.counter("price_runtime_config_cache_error_total", "layer", "l2", "op", "set").increment();
		}
	}

	private void evictRedisSnapshot() {
		if (!isRedisCacheEnabled()) {
			return;
		}
		int maxAttempts = Math.max(1, cacheProperties.getL2DeleteRetryMax());
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				stringRedisTemplate.delete(cacheProperties.snapshotKey());
				if (attempt > 1) {
					log.info("price.runtime.config.redis.delete_recovered attempt={}", attempt);
				}
				return;
			} catch (Exception ex) {
				meterRegistry.counter("price_runtime_config_cache_error_total", "layer", "l2", "op", "del").increment();
				if (attempt >= maxAttempts) {
					log.warn("price.runtime.config.redis.delete_failed attempts={} error={}", attempt, ex.getMessage());
					return;
				}
				log.warn("price.runtime.config.redis.delete_retry attempt={} error={}", attempt, ex.getMessage());
				pauseDeleteRetryBackoff();
			}
		}
	}

	private void publishInvalidation() {
		if (!isRedisCacheEnabled() || !cacheProperties.isInvalidationEnabled()) {
			return;
		}
		try {
			String payload = objectMapper.writeValueAsString(new RuntimeConfigInvalidationMessage(System.currentTimeMillis()));
			stringRedisTemplate.convertAndSend(cacheProperties.getInvalidationChannel(), payload);
			meterRegistry.counter("price_runtime_config_invalidation_publish_total").increment();
		} catch (JsonProcessingException ex) {
			log.warn("price.runtime.config.redis.publish_serialize_failed error={}", ex.getMessage());
			meterRegistry.counter("price_runtime_config_cache_error_total", "layer", "l2", "op", "pub_serialize").increment();
		} catch (Exception ex) {
			log.warn("price.runtime.config.redis.publish_failed error={}", ex.getMessage());
			meterRegistry.counter("price_runtime_config_cache_error_total", "layer", "l2", "op", "pub").increment();
		}
	}

	private void invalidateLocalCache(String scope) {
		snapshotCache.invalidateAll();
		meterRegistry.counter("price_runtime_config_cache_refresh_total", "scope", scope).increment();
	}

	private boolean isRedisCacheEnabled() {
		return cacheProperties.isL2Enabled() && stringRedisTemplate != null;
	}

	private int resolvePriority(PriceProviderConfigEntity provider) {
		Integer priority = provider.getPriority();
		if (priority != null && priority > 0) {
			return priority;
		}
		log.warn("price.runtime.config.priority.invalid provider={} value={} fallback={}",
			provider.getProviderName(),
			priority,
			Integer.MAX_VALUE);
		recordDbFallback("priority", "invalid_non_positive", normalizeProviderName(provider.getProviderName()));
		return Integer.MAX_VALUE;
	}

	private PriorityValue resolvePriorityValue(RuntimeConfigSnapshot.ProviderConfigSnapshot provider) {
		Integer priority = provider.priority();
		if (priority != null && priority > 0) {
			return new PriorityValue(priority, "db");
		}
		return new PriorityValue(Integer.MAX_VALUE, "fallback_default");
	}

	private void recordDbFallback(String scene, String reason, String provider) {
		lastFallbackScene = scene;
		lastFallbackReason = reason;
		lastFallbackProvider = provider;
		meterRegistry.counter(
			"price_runtime_config_db_fallback_total",
			"scene", scene,
			"reason", reason,
			"provider", provider
		).increment();
	}

	private String formatLastFallback() {
		if (!StringUtils.hasText(lastFallbackScene)) {
			return "none";
		}
		return lastFallbackScene + ":" + lastFallbackReason + ":" + lastFallbackProvider;
	}

	private void logRefreshSnapshot() {
		try {
			logSnapshot("refresh", loadSnapshotFromDb());
		} catch (Exception ex) {
			log.warn("price.runtime.config.refresh.failed error={}", ex.getMessage());
		}
	}

	private void logSnapshot(String scene, RuntimeConfigSnapshot snapshot) {
		StringBuilder details = new StringBuilder();
		for (RuntimeConfigSnapshot.ProviderConfigSnapshot provider : snapshot.providers().values()) {
			PriorityValue priorityValue = resolvePriorityValue(provider);
			String baseUrl = StringUtils.hasText(provider.baseUrl()) ? provider.baseUrl().trim() : "blank";
			String baseUrlSource = StringUtils.hasText(provider.baseUrl()) ? "db" : "fallback_default";
			Integer timeoutMs = provider.timeoutMs();
			String timeoutSource = timeoutMs != null && timeoutMs > 0 ? "db" : "fallback_default";
			if (details.length() > 0) {
				details.append(", ");
			}
			details.append(provider.providerName())
				.append("{priority=").append(priorityValue.value())
				.append("(source=").append(priorityValue.source()).append(")")
				.append(",baseUrl=").append(baseUrl)
				.append("(source=").append(baseUrlSource).append(")")
				.append(",timeoutMs=").append(timeoutMs)
				.append("(source=").append(timeoutSource).append(")")
				.append("}");
		}
		if (snapshot.priorities().isEmpty()) {
			log.warn("price.runtime.config.{} enabledProviders=0 priorities={} details=[{}] lastFallback={}",
				scene, snapshot.priorities(), details, formatLastFallback());
			return;
		}
		log.info("price.runtime.config.{} enabledProviders={} priorities={} details=[{}] lastFallback={}",
			scene, snapshot.priorities().size(), snapshot.priorities(), details, formatLastFallback());
	}

	private String normalizeProviderName(String providerName) {
		return providerName.trim().toLowerCase(Locale.ROOT);
	}

	private Duration resolveL2TtlWithJitter() {
		Duration baseTtl = cacheProperties.getL2Ttl();
		if (baseTtl == null || baseTtl.isNegative() || baseTtl.isZero()) {
			baseTtl = Duration.ofSeconds(90);
		}
		double jitterRatio = cacheProperties.getL2TtlJitterRatio();
		if (jitterRatio <= 0) {
			return baseTtl;
		}
		long baseMillis = baseTtl.toMillis();
		long maxJitterMillis = Math.max(1L, Math.round(baseMillis * jitterRatio));
		long jitterMillis = ThreadLocalRandom.current().nextLong(maxJitterMillis + 1L);
		return Duration.ofMillis(baseMillis + jitterMillis);
	}

	private void pauseDeleteRetryBackoff() {
		Duration backoff = cacheProperties.getL2DeleteRetryBackoff();
		if (backoff == null || backoff.isNegative() || backoff.isZero()) {
			return;
		}
		try {
			Thread.sleep(backoff.toMillis());
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private static Duration resolveL1Ttl(RuntimeConfigCacheProperties cacheProperties) {
		Duration ttl = cacheProperties.getL1Ttl();
		return ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(10) : ttl;
	}

	private record PriorityValue(int value, String source) {
	}
}
