package com.chainsentinel.infra.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.chainsentinel.infra.entity.PriceProviderConfigEntity;
import com.chainsentinel.infra.repository.PriceProviderConfigRepository;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Primary
public class DbPriceProviderRuntimeConfig implements PriceProviderRuntimeConfig {

	private static final Logger log = LoggerFactory.getLogger(DbPriceProviderRuntimeConfig.class);
	private static final String PRIORITY_CACHE_KEY = "priorities";
	private static final Duration CACHE_TTL = Duration.ofSeconds(10);

	private final PriceProviderConfigRepository priceProviderConfigRepository;
	private final MeterRegistry meterRegistry;
	private final Cache<String, Optional<PriceProviderConfigEntity>> providerCache;
	private final Cache<String, Map<String, Integer>> priorityCache;

	private volatile String lastFallbackScene;
	private volatile String lastFallbackReason;
	private volatile String lastFallbackProvider;

	public DbPriceProviderRuntimeConfig(PriceProviderConfigRepository priceProviderConfigRepository,
	                                    MeterRegistry meterRegistry) {
		this.priceProviderConfigRepository = priceProviderConfigRepository;
		this.meterRegistry = meterRegistry;
		this.providerCache = Caffeine.newBuilder().expireAfterWrite(CACHE_TTL).build();
		this.priorityCache = Caffeine.newBuilder().expireAfterWrite(CACHE_TTL).build();
	}

	@PostConstruct
	public void logStartupProviderConfig() {
		try {
			List<PriceProviderConfigEntity> enabledProviders =
				priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc();
			StartupSnapshot snapshot = buildStartupSnapshot(enabledProviders);
			if (snapshot.priorities().isEmpty()) {
				log.warn("price.runtime.config.startup enabledProviders=0 priorities={} details=[{}] lastFallback={}",
					snapshot.priorities(),
					snapshot.details(),
					formatLastFallback());
				return;
			}
			log.info("price.runtime.config.startup enabledProviders={} priorities={} details=[{}] lastFallback={}",
				snapshot.priorities().size(),
				snapshot.priorities(),
				snapshot.details(),
				formatLastFallback());
		} catch (Exception ex) {
			log.warn("price.runtime.config.startup.failed error={}", ex.getMessage());
		}
	}

	@Override
	public Map<String, Integer> providerPriority() {
		Map<String, Integer> cached = priorityCache.getIfPresent(PRIORITY_CACHE_KEY);
		if (cached != null) {
			return cached;
		}
		List<PriceProviderConfigEntity> enabledProviders;
		try {
			enabledProviders = priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc();
		} catch (Exception ex) {
			log.warn("price.runtime.config.priority.load_failed error={}", ex.getMessage());
			recordDbFallback("priority", "db_error", "all");
			return Map.of();
		}
		Map<String, Integer> priorities = new LinkedHashMap<>();
		for (PriceProviderConfigEntity provider : enabledProviders) {
			if (!StringUtils.hasText(provider.getProviderName())) {
				log.warn("price.runtime.config.priority.invalid providerName=blank");
				recordDbFallback("priority", "invalid_provider_name", "blank");
				continue;
			}
			int priority = resolvePriority(provider);
			priorities.put(normalizeProviderName(provider.getProviderName()), priority);
		}
		priorityCache.put(PRIORITY_CACHE_KEY, priorities);
		return priorities;
	}

	@Override
	public boolean providerEnabled(String providerName) {
		if (!StringUtils.hasText(providerName)) {
			return false;
		}
		try {
			return priceProviderConfigRepository.findByProviderNameAndEnabledTrue(normalizeProviderName(providerName)).isPresent();
		} catch (Exception ex) {
			log.warn("price.runtime.config.enabled.load_failed provider={} error={}", providerName, ex.getMessage());
			recordDbFallback("enabled", "db_error", normalizeProviderName(providerName));
			return false;
		}
	}

	@Override
	public String providerBaseUrl(String providerName, String defaultBaseUrl) {
		Optional<PriceProviderConfigEntity> providerOpt = findEnabledProvider(providerName);
		if (providerOpt.isPresent()) {
			String baseUrl = providerOpt.get().getBaseUrl();
			if (StringUtils.hasText(baseUrl)) {
				return baseUrl.trim();
			}
			log.warn("price.runtime.config.base_url.invalid provider={} reason=blank fallback={}", providerName,
				defaultBaseUrl);
			recordDbFallback("base_url", "invalid_blank", normalizeProviderName(providerName));
		}
		return defaultBaseUrl;
	}

	@Override
	public int providerTimeoutMs(String providerName, int defaultTimeoutMs) {
		Optional<PriceProviderConfigEntity> providerOpt = findEnabledProvider(providerName);
		if (providerOpt.isPresent()) {
			Integer timeoutMs = providerOpt.get().getTimeoutMs();
			if (timeoutMs != null && timeoutMs > 0) {
				return timeoutMs;
			}
			log.warn("price.runtime.config.timeout.invalid provider={} value={} fallback={}", providerName, timeoutMs,
				defaultTimeoutMs);
			recordDbFallback("timeout", "invalid_non_positive", normalizeProviderName(providerName));
		}
		return defaultTimeoutMs;
	}

	@Override
	public void refreshCache() {
		providerCache.invalidateAll();
		priorityCache.invalidateAll();
		// Reserved for future distributed invalidation (e.g. MQ/event-bus) when multi-instance deployment is
		// introduced.
		log.info("price.runtime.config.cache.refreshed scope=local");
		meterRegistry.counter("price_runtime_config_cache_refresh_total", "scope", "local").increment();
		logRefreshSnapshot();
	}

	private Optional<PriceProviderConfigEntity> findEnabledProvider(String providerName) {
		if (!StringUtils.hasText(providerName)) {
			return Optional.empty();
		}
		String normalized = normalizeProviderName(providerName);
		Optional<PriceProviderConfigEntity> cached = providerCache.getIfPresent(normalized);
		if (cached != null) {
			return cached;
		}
		Optional<PriceProviderConfigEntity> loaded;
		try {
			loaded = priceProviderConfigRepository.findByProviderNameAndEnabledTrue(normalized);
		} catch (Exception ex) {
			log.warn("price.runtime.config.provider.load_failed provider={} error={}", normalized, ex.getMessage());
			recordDbFallback("provider_lookup", "db_error", normalized);
			loaded = Optional.empty();
		}
		providerCache.put(normalized, loaded);
		return loaded;
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

	private PriorityValue resolvePriorityValue(PriceProviderConfigEntity provider) {
		Integer priority = provider.getPriority();
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

	private StartupSnapshot buildStartupSnapshot(List<PriceProviderConfigEntity> enabledProviders) {
		Map<String, Integer> priorities = new LinkedHashMap<>();
		StringBuilder details = new StringBuilder();
		for (PriceProviderConfigEntity provider : enabledProviders) {
			if (!StringUtils.hasText(provider.getProviderName())) {
				continue;
			}
			String providerName = normalizeProviderName(provider.getProviderName());
			PriorityValue priorityValue = resolvePriorityValue(provider);
			priorities.put(providerName, priorityValue.value());
			String baseUrl = StringUtils.hasText(provider.getBaseUrl()) ? provider.getBaseUrl().trim() : "blank";
			String baseUrlSource = StringUtils.hasText(provider.getBaseUrl()) ? "db" : "fallback_default";
			Integer timeoutMs = provider.getTimeoutMs();
			String timeoutSource = timeoutMs != null && timeoutMs > 0 ? "db" : "fallback_default";
			if (details.length() > 0) {
				details.append(", ");
			}
			details.append(providerName)
				.append("{priority=").append(priorityValue.value())
				.append("(source=").append(priorityValue.source()).append(")")
				.append(",baseUrl=").append(baseUrl)
				.append("(source=").append(baseUrlSource).append(")")
				.append(",timeoutMs=").append(timeoutMs)
				.append("(source=").append(timeoutSource).append(")")
				.append("}");
		}
		return new StartupSnapshot(priorities, details.toString());
	}

	private void logRefreshSnapshot() {
		try {
			List<PriceProviderConfigEntity> enabledProviders =
				priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc();
			StartupSnapshot snapshot = buildStartupSnapshot(enabledProviders);
			log.info("price.runtime.config.refresh snapshotProviders={} priorities={} details=[{}] lastFallback={}",
				snapshot.priorities().size(),
				snapshot.priorities(),
				snapshot.details(),
				formatLastFallback());
		} catch (Exception ex) {
			log.warn("price.runtime.config.refresh.failed error={}", ex.getMessage());
		}
	}

	private String normalizeProviderName(String providerName) {
		return providerName.trim().toLowerCase(Locale.ROOT);
	}

	private record PriorityValue(int value, String source) {
	}

	private record StartupSnapshot(Map<String, Integer> priorities, String details) {
	}

}