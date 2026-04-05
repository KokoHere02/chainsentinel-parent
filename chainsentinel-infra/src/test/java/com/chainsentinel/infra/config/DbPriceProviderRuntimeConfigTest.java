package com.chainsentinel.infra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.entity.PriceProviderConfigEntity;
import com.chainsentinel.infra.repository.PriceProviderConfigRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbPriceProviderRuntimeConfigTest {

  @Mock
  private PriceProviderConfigRepository priceProviderConfigRepository;

  @Test
  void shouldFilterByEnabledWhenCheckProviderEnabled() {
    when(priceProviderConfigRepository.findByProviderNameAndEnabledTrue("okx"))
      .thenReturn(Optional.empty());
    when(priceProviderConfigRepository.findByProviderNameAndEnabledTrue("binance"))
      .thenReturn(Optional.of(provider("binance", "https://api.binance.com", 2, 1500)));

    DbPriceProviderRuntimeConfig config = new DbPriceProviderRuntimeConfig(priceProviderConfigRepository);

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

    DbPriceProviderRuntimeConfig config = new DbPriceProviderRuntimeConfig(priceProviderConfigRepository);
    Map<String, Integer> priorities = config.providerPriority();

    assertIterableEquals(List.of("okx", "binance"), new ArrayList<>(priorities.keySet()));
    assertEquals(1, priorities.get("okx"));
    assertEquals(2, priorities.get("binance"));
  }

  @Test
  void shouldReadBaseUrlAndTimeoutFromDbOrFallbackToDefault() {
    PriceProviderConfigEntity okx = provider("okx", " https://okx.com ", 1, 2500);
    when(priceProviderConfigRepository.findByProviderNameAndEnabledTrue("okx"))
      .thenReturn(Optional.of(okx));
    when(priceProviderConfigRepository.findByProviderNameAndEnabledTrue("missing"))
      .thenReturn(Optional.empty());

    DbPriceProviderRuntimeConfig config = new DbPriceProviderRuntimeConfig(priceProviderConfigRepository);

    assertEquals("https://okx.com", config.providerBaseUrl("okx", "https://www.okx.com"));
    assertEquals(2500, config.providerTimeoutMs("okx", 1500));

    assertEquals("https://default.example", config.providerBaseUrl("missing", "https://default.example"));
    assertEquals(1800, config.providerTimeoutMs("missing", 1800));
  }

  @Test
  void shouldFallbackToDefaultWhenProviderConfigIsInvalid() {
    PriceProviderConfigEntity invalid = provider("okx", "   ", 0, 0);
    when(priceProviderConfigRepository.findByProviderNameAndEnabledTrue("okx"))
      .thenReturn(Optional.of(invalid));
    when(priceProviderConfigRepository.findByEnabledTrueOrderByPriorityAscIdAsc())
      .thenReturn(List.of(invalid));

    DbPriceProviderRuntimeConfig config = new DbPriceProviderRuntimeConfig(priceProviderConfigRepository);

    Map<String, Integer> priorities = config.providerPriority();
    assertEquals(Integer.MAX_VALUE, priorities.get("okx"));
    assertEquals("https://default.example", config.providerBaseUrl("okx", "https://default.example"));
    assertEquals(1500, config.providerTimeoutMs("okx", 1500));
  }

  @Test
  void shouldReturnSafeDefaultsWhenRepositoryThrows() {
    doThrow(new RuntimeException("db down"))
      .when(priceProviderConfigRepository).findByEnabledTrueOrderByPriorityAscIdAsc();
    doThrow(new RuntimeException("db down"))
      .when(priceProviderConfigRepository).findByProviderNameAndEnabledTrue("okx");

    DbPriceProviderRuntimeConfig config = new DbPriceProviderRuntimeConfig(priceProviderConfigRepository);

    assertTrue(config.providerPriority().isEmpty());
    assertFalse(config.providerEnabled("okx"));
    assertEquals("https://default.example", config.providerBaseUrl("okx", "https://default.example"));
    assertEquals(1500, config.providerTimeoutMs("okx", 1500));
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
