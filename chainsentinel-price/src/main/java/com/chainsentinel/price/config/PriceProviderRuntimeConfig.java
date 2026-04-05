package com.chainsentinel.price.config;

import java.util.Map;

public interface PriceProviderRuntimeConfig {

  Map<String, Integer> providerPriority();

  boolean providerEnabled(String providerName);

  String providerBaseUrl(String providerName, String defaultBaseUrl);

  int providerTimeoutMs(String providerName, int defaultTimeoutMs);
}