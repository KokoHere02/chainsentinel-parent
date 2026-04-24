package com.chainsentinel.web.api;

import com.chainsentinel.core.service.AddressHoldingQueryService;
import com.chainsentinel.core.service.dto.AddressTokenHoldingView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/holdings")
@Validated
public class HoldingController {

	private final AddressHoldingQueryService addressHoldingQueryService;

	public HoldingController(AddressHoldingQueryService addressHoldingQueryService) {
		this.addressHoldingQueryService = addressHoldingQueryService;
	}

	@GetMapping
	public List<AddressTokenHoldingView> list(
		@RequestParam(name = "chain", required = false) String chain,
		@RequestParam(name = "network", required = false) String network,
		@RequestParam(name = "address", required = false) String address,
		@RequestParam(name = "limit", defaultValue = "50") int limit
	) {
		if (limit < 1 || limit > 200) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 200");
		}
		return addressHoldingQueryService.list(chain, network, address, limit);
	}
}

