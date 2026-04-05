package com.chainsentinel.price.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import com.chainsentinel.price.cache.PriceCache;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import com.chainsentinel.price.provider.ProviderRouter;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultPriceServiceTest {

  @Mock
  private ProviderRouter providerRouter;

  @Mock
  private PriceCache priceCache;

  @Mock
  private PriceProviderRuntimeConfig runtimeConfig;

  @Test
  void shouldFallbackToCachedStaleQuoteWhenProviderDisabledInDbAndRealtimeFetchIsEmpty() {
    PriceQuery query = new PriceQuery("OFFCHAIN", PriceInstType.SPOT, "ETH", "USDT", null);
    PriceQuote cachedFreshQuote = new PriceQuote(
      "ETH",
      "USDT",
      new BigDecimal("3500.12"),
      1711910400000L,
      "okx",
      false
    );

    Map<String, Integer> priorities = new LinkedHashMap<>();
    priorities.put("okx", 1);

    when(runtimeConfig.providerPriority()).thenReturn(priorities);
    when(providerRouter.getQuote(eq(query), eq(priorities))).thenReturn(Optional.empty());
    when(priceCache.get(query)).thenReturn(Optional.of(cachedFreshQuote));

    DefaultPriceService service = new DefaultPriceService(providerRouter, priceCache, runtimeConfig);
    Optional<PriceQuote> result = service.getQuote(query);

    assertTrue(result.isPresent());
    assertEquals("ETH", result.get().baseSymbol());
    assertEquals("USDT", result.get().quoteSymbol());
    assertEquals("3500.12", result.get().price().toPlainString());
    assertEquals("okx", result.get().source());
    assertTrue(result.get().stale());

    verify(providerRouter).getQuote(eq(query), eq(priorities));
    verify(priceCache).get(query);
  }

  @Test
  void shouldReturnEmptyWhenAllProvidersUnavailableAndCacheMissing() {
    PriceQuery query = new PriceQuery("OFFCHAIN", PriceInstType.SPOT, "BTC", "USDT", null);
    Map<String, Integer> priorities = new LinkedHashMap<>();
    priorities.put("okx", 1);

    when(runtimeConfig.providerPriority()).thenReturn(priorities);
    when(providerRouter.getQuote(eq(query), eq(priorities))).thenReturn(Optional.empty());
    when(priceCache.get(query)).thenReturn(Optional.empty());

    DefaultPriceService service = new DefaultPriceService(providerRouter, priceCache, runtimeConfig);
    Optional<PriceQuote> result = service.getQuote(query);

    assertTrue(result.isEmpty());
    verify(providerRouter).getQuote(eq(query), eq(priorities));
    verify(priceCache).get(query);
  }

  @Test
  void shouldFallbackToCacheWhenDbPriorityLoadFailsAndRuntimeUsesDefaultPriority() {
    PriceQuery query = new PriceQuery("OFFCHAIN", PriceInstType.SPOT, "SOL", "USDT", null);
    PriceQuote cachedFreshQuote = new PriceQuote(
      "SOL",
      "USDT",
      new BigDecimal("150.01"),
      1711910400000L,
      "okx",
      false
    );

    Map<String, Integer> defaultPriorities = new LinkedHashMap<>();
    defaultPriorities.put("okx", 1);

    when(runtimeConfig.providerPriority()).thenReturn(Map.of());
    when(providerRouter.getQuote(eq(query), eq(defaultPriorities))).thenReturn(Optional.empty());
    when(priceCache.get(query)).thenReturn(Optional.of(cachedFreshQuote));

    DefaultPriceService service = new DefaultPriceService(providerRouter, priceCache, runtimeConfig);
    Optional<PriceQuote> result = service.getQuote(query);

    assertTrue(result.isPresent());
    assertEquals("SOL", result.get().baseSymbol());
    assertEquals("USDT", result.get().quoteSymbol());
    assertEquals("150.01", result.get().price().toPlainString());
    assertEquals("okx", result.get().source());
    assertTrue(result.get().stale());
    assertFalse(cachedFreshQuote.stale());

    verify(providerRouter).getQuote(eq(query), eq(defaultPriorities));
    verify(priceCache).get(query);
  }
}
