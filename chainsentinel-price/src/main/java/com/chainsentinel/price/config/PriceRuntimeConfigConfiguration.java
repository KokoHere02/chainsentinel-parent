package com.chainsentinel.price.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PriceRuntimeConfigConfiguration {

  @Bean
  @ConditionalOnMissingBean(PriceProviderRuntimeConfig.class)
  public PriceProviderRuntimeConfig priceProviderRuntimeConfig() {
    return new DefaultPriceProviderRuntimeConfig();
  }

  static class DefaultPriceProviderRuntimeConfig implements PriceProviderRuntimeConfig {

    @Override
    public Map<String, Integer> providerPriority() {
      Map<String, Integer> defaults = new LinkedHashMap<>();
      defaults.put("okx", 1);
      return defaults;
    }

    @Override
    public boolean providerEnabled(String providerName) {
      return "okx".equalsIgnoreCase(providerName);
    }

    @Override
    public String providerBaseUrl(String providerName, String defaultBaseUrl) {
      return defaultBaseUrl;
    }

    @Override
    public int providerTimeoutMs(String providerName, int defaultTimeoutMs) {
      return defaultTimeoutMs;
    }
  }
}