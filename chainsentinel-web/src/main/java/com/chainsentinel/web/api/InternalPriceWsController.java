package com.chainsentinel.web.api;

import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.stream.PriceStreamManager;
import com.chainsentinel.price.stream.PriceStreamProviderStatus;
import com.chainsentinel.price.stream.PriceStreamStatusService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/price-ws")
public class InternalPriceWsController {

	private final PriceStreamStatusService priceStreamStatusService;
	private final PriceStreamManager priceStreamManager;

	public InternalPriceWsController(
		PriceStreamStatusService priceStreamStatusService,
		PriceStreamManager priceStreamManager
	) {
		this.priceStreamStatusService = priceStreamStatusService;
		this.priceStreamManager = priceStreamManager;
	}

	@GetMapping("/status")
	public List<PriceStreamProviderStatus> status() {
		return priceStreamStatusService.listStatuses();
	}

	@GetMapping("/subscriptions")
	public List<PriceWsProviderSubscriptionsView> subscriptions() {
		return priceStreamManager.currentEffectiveSubscriptions().entrySet().stream()
			.map(entry -> new PriceWsProviderSubscriptionsView(
				entry.getKey(),
				entry.getValue().stream().map(this::toSubscriptionView).toList()
			))
			.toList();
	}

	private PriceWsSubscriptionView toSubscriptionView(PriceQuery query) {
		if (query == null) {
			return new PriceWsSubscriptionView(null, null, null, null, null, null);
		}
		return new PriceWsSubscriptionView(
			query.chain(),
			query.instType() == null ? null : query.instType().name(),
			query.symbol(),
			query.quoteSymbol(),
			query.tokenAddress(),
			query.normalizedInstId()
		);
	}

	public record PriceWsProviderSubscriptionsView(String provider, List<PriceWsSubscriptionView> queries) {
	}

	public record PriceWsSubscriptionView(
		String chain,
		String instType,
		String symbol,
		String quoteSymbol,
		String tokenAddress,
		String instId
	) {
	}
}
