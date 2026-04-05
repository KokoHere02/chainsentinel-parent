package com.chainsentinel.infra.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.chainsentinel.infra.entity.PriceProviderConfigEntity;
import com.chainsentinel.infra.repository.PriceProviderConfigRepository;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

  public DbPriceProviderRuntimeConfig(PriceProviderConfigRepository priceProviderConfigRepository, MeterRegistry meterRegistry) {
    this.priceProviderConfigRepository = priceProviderConfigRepository;
    this.meterRegistry = meterRegistry;
    this.providerCache = Caffeine.newBuilder().expireAfterWrite(CACHE_TTL).build();
    this.priorityCache = Caffeine.newBuilder().expireAfterWrite(CACHE_TTL).build();
  }

  @PostConstruct
  public void logStartupProviderConfig() {
    try {
      Map<String, Integer> priorities = providerPriority();
      if (priorities.isEmpty()) {
        log.warn("price.runtime.config.startup enabledProviders=0 priorities={}");
        return;
      }
      log.info("price.runtime.config.startup enabledProviders={} priorities={}", priorities.size(), priorities);
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
      log.warn("price.runtime.config.base_url.invalid provider={} reason=blank fallback={}", providerName, defaultBaseUrl);
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
      log.warn("price.runtime.config.timeout.invalid provider={} value={} fallback={}", providerName, timeoutMs, defaultTimeoutMs);
      recordDbFallback("timeout", "invalid_non_positive", normalizeProviderName(providerName));
    }
    return defaultTimeoutMs;
  }

  @Override
  public void refreshCache() {
    providerCache.invalidateAll();
    priorityCache.invalidateAll();
    // Reserved for future distributed invalidation (e.g. MQ/event-bus) when multi-instance deployment is introduced.
    log.info("price.runtime.config.cache.refreshed scope=local");
    meterRegistry.counter("price_runtime_config_cache_refresh_total", "scope", "local").increment();
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

  private void recordDbFallback(String scene, String reason, String provider) {
    meterRegistry.counter(
      "price_runtime_config_db_fallback_total",
      "scene", scene,
      "reason", reason,
      "provider", provider
    ).increment();
  }

  private String normalizeProviderName(String providerName) {
    return providerName.trim().toLowerCase(Locale.ROOT);
  }
}
