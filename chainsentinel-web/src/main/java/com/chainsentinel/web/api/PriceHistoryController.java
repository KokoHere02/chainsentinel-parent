package com.chainsentinel.web.api;

import com.chainsentinel.infra.service.PriceTickQueryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/prices/history")
public class PriceHistoryController {

	private final PriceTickQueryService priceTickQueryService;

	public PriceHistoryController(PriceTickQueryService priceTickQueryService) {
		this.priceTickQueryService = priceTickQueryService;
	}

	@GetMapping
	public List<PriceTickQueryService.PriceTickView> query(
		@RequestParam(name = "instId") String instId,
		@RequestParam(name = "provider", required = false) String provider,
		@RequestParam(name = "from", required = false) Long fromTs,
		@RequestParam(name = "to", required = false) Long toTs,
		@RequestParam(name = "limit", defaultValue = "500") int limit
	) {
		if (limit < 1 || limit > 5000) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 5000");
		}
		if (fromTs != null && toTs != null && fromTs > toTs) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be less than or equal to to");
		}
		return priceTickQueryService.query(provider, instId, fromTs, toTs, limit);
	}

	@GetMapping("/aggregate")
	public List<PriceTickQueryService.PriceTickAggregateView> aggregate(
		@RequestParam(name = "instId") String instId,
		@RequestParam(name = "provider", required = false) String provider,
		@RequestParam(name = "from", required = false) Long fromTs,
		@RequestParam(name = "to", required = false) Long toTs,
		@RequestParam(name = "bucketMs", defaultValue = "60000") long bucketMs,
		@RequestParam(name = "limit", defaultValue = "5000") int limit
	) {
		if (limit < 1 || limit > 20000) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 20000");
		}
		if (bucketMs < 1000 || bucketMs > 86400000L) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bucketMs must be between 1000 and 86400000");
		}
		if (fromTs != null && toTs != null && fromTs > toTs) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be less than or equal to to");
		}
		return priceTickQueryService.aggregate(provider, instId, fromTs, toTs, bucketMs, limit);
	}
}