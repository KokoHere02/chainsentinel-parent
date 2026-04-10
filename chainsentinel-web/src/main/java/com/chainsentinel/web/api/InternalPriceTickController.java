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
@RequestMapping("/api/internal/price-ticks")
public class InternalPriceTickController {

	private final PriceTickQueryService priceTickQueryService;

	public InternalPriceTickController(PriceTickQueryService priceTickQueryService) {
		this.priceTickQueryService = priceTickQueryService;
	}

	@GetMapping
	public List<PriceTickQueryService.PriceTickView> query(
		@RequestParam(name = "provider", required = false) String provider,
		@RequestParam(name = "instId", required = false) String instId,
		@RequestParam(name = "from", required = false) Long fromTs,
		@RequestParam(name = "to", required = false) Long toTs,
		@RequestParam(name = "limit", defaultValue = "200") int limit
	) {
		if (limit < 1 || limit > 5000) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 5000");
		}
		if (fromTs != null && toTs != null && fromTs > toTs) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be less than or equal to to");
		}
		return priceTickQueryService.query(provider, instId, fromTs, toTs, limit);
	}
}

