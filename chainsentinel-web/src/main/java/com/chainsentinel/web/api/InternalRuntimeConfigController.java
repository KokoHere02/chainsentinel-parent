package com.chainsentinel.web.api;

import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/runtime-config")
public class InternalRuntimeConfigController {

  private final PriceProviderRuntimeConfig priceProviderRuntimeConfig;

  public InternalRuntimeConfigController(PriceProviderRuntimeConfig priceProviderRuntimeConfig) {
    this.priceProviderRuntimeConfig = priceProviderRuntimeConfig;
  }

  @PostMapping("/price/refresh")
  public RefreshResponse refreshPriceRuntimeConfigCache() {
    priceProviderRuntimeConfig.refreshCache();
    return new RefreshResponse(true, Instant.now());
  }

  public record RefreshResponse(boolean refreshed, Instant refreshedAt) {
  }
}
