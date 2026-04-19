package com.chainsentinel.web.api;

import com.chainsentinel.infra.job.PriceStreamSubscriptionJob;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import com.chainsentinel.web.api.support.ratelimit.RateLimit;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/runtime-config")
public class InternalRuntimeConfigController {

	private final PriceProviderRuntimeConfig priceProviderRuntimeConfig;
	private final PriceStreamSubscriptionJob priceStreamSubscriptionJob;

	public InternalRuntimeConfigController(
		PriceProviderRuntimeConfig priceProviderRuntimeConfig,
		PriceStreamSubscriptionJob priceStreamSubscriptionJob
	) {
		this.priceProviderRuntimeConfig = priceProviderRuntimeConfig;
		this.priceStreamSubscriptionJob = priceStreamSubscriptionJob;
	}

	@PostMapping("/price/refresh")
	@RateLimit(
		name = "internal.runtime-config.price.refresh",
		permits = 6,
		windowSeconds = 10,
		scope = RateLimit.Scope.IP,
		message = "Refresh too frequent, retry later"
	)
	public RefreshResponse refreshPriceRuntimeConfigCache() {
		priceProviderRuntimeConfig.refreshCache();
		return new RefreshResponse(true, Instant.now());
	}

	@PostMapping("/price-ws/refresh")
	@RateLimit(
		name = "internal.runtime-config.price-ws.refresh",
		permits = 4,
		windowSeconds = 10,
		scope = RateLimit.Scope.IP,
		message = "WS refresh too frequent, retry later"
	)
	public RefreshResponse refreshPriceWsSubscriptions() {
		priceStreamSubscriptionJob.refreshSubscriptions();
		return new RefreshResponse(true, Instant.now());
	}

	public record RefreshResponse(boolean refreshed, Instant refreshedAt) {
	}
}