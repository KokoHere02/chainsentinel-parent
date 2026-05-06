package com.chainsentinel.web.api;

import com.chainsentinel.price.api.PriceMarketDataService;
import com.chainsentinel.price.api.dto.PriceOrderBook;
import com.chainsentinel.price.api.dto.PricePublicTrade;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/prices/market")
public class PriceMarketController {

	private static final String DEFAULT_PROVIDER = "okx";

	private final PriceMarketDataService priceMarketDataService;

	public PriceMarketController(PriceMarketDataService priceMarketDataService) {
		this.priceMarketDataService = priceMarketDataService;
	}

	@GetMapping("/depth")
	public PriceOrderBook depth(
		@RequestParam(name = "instId") String instId,
		@RequestParam(name = "provider", required = false) String provider,
		@RequestParam(name = "depth", defaultValue = "20") int depth
	) {
		if (depth < 1 || depth > 400) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "depth must be between 1 and 400");
		}
		return priceMarketDataService.getOrderBook(resolveProvider(provider), instId, depth);
	}

	@GetMapping("/trades")
	public List<PricePublicTrade> recentTrades(
		@RequestParam(name = "instId") String instId,
		@RequestParam(name = "provider", required = false) String provider,
		@RequestParam(name = "limit", defaultValue = "50") int limit
	) {
		if (limit < 1 || limit > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 100");
		}
		return priceMarketDataService.getRecentPublicTrades(resolveProvider(provider), instId, limit);
	}

	private String resolveProvider(String provider) {
		if (provider == null || provider.isBlank()) {
			return DEFAULT_PROVIDER;
		}
		return provider;
	}
}
