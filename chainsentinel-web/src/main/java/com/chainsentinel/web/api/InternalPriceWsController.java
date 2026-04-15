package com.chainsentinel.web.api;

import com.chainsentinel.price.stream.PriceStreamProviderStatus;
import com.chainsentinel.price.stream.PriceStreamStatusService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/price-ws")
public class InternalPriceWsController {

	private final PriceStreamStatusService priceStreamStatusService;

	public InternalPriceWsController(PriceStreamStatusService priceStreamStatusService) {
		this.priceStreamStatusService = priceStreamStatusService;
	}

	@GetMapping("/status")
	public List<PriceStreamProviderStatus> status() {
		return priceStreamStatusService.listStatuses();
	}
}