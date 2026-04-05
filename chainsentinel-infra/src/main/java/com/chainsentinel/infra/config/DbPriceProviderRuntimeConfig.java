package com.chainsentinel.infra.config;

import com.chainsentinel.infra.entity.PriceProviderConfigEntity;
import com.chainsentinel.infra.repository.PriceProviderConfigRepository;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Primary
public class DbPriceProviderRuntimeConfig implements PriceProviderRuntimeConfig {

  private final PriceProviderConfigRepository priceProviderConfigRepository;

  public DbPriceProviderRuntimeConfig(PriceProviderConfigRepository priceProviderConfigRepository) {
    this.priceProviderConfigRepository = priceProviderConfigRepository;
  }

  @Override
  public Map<String, Integer> providerPriority() {
    List<PriceProviderConfigEntity> enabledProviders = priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc();
    Map<String, Integer> priorities = new LinkedHashMap<>();
    for (PriceProviderConfigEntity provider : enabledProviders) {
      if (!StringUtils.hasText(provider.getProviderName())) {
        continue;
      }
      int priority = provider.getPriority() == null ? Integer.MAX_VALUE : provider.getPriority();
      priorities.put(normalizeProviderName(provider.getProviderName()), priority);
    }
    return priorities;
  }

  @Override
  public boolean providerEnabled(String providerName) {
    if (!StringUtils.hasText(providerName)) {
      return false;
    }
    return priceProviderConfigRepository.findByProviderNameAndEnabledTrue(normalizeProviderName(providerName)).isPresent();
  }

  @Override
  public String providerBaseUrl(String providerName, String defaultBaseUrl) {
    Optional<PriceProviderConfigEntity> providerOpt = findEnabledProvider(providerName);
    if (providerOpt.isPresent() && StringUtils.hasText(providerOpt.get().getBaseUrl())) {
      return providerOpt.get().getBaseUrl().trim();
    }
    return defaultBaseUrl;
  }

  @Override
  public int providerTimeoutMs(String providerName, int defaultTimeoutMs) {
    Optional<PriceProviderConfigEntity> providerOpt = findEnabledProvider(providerName);
    if (providerOpt.isPresent() && providerOpt.get().getTimeoutMs() != null && providerOpt.get().getTimeoutMs() > 0) {
      return providerOpt.get().getTimeoutMs();
    }
    return defaultTimeoutMs;
  }

  private Optional<PriceProviderConfigEntity> findEnabledProvider(String providerName) {
    if (!StringUtils.hasText(providerName)) {
      return Optional.empty();
    }
    return priceProviderConfigRepository.findByProviderNameAndEnabledTrue(normalizeProviderName(providerName));
  }

  private String normalizeProviderName(String providerName) {
    return providerName.trim().toLowerCase(Locale.ROOT);
  }
}