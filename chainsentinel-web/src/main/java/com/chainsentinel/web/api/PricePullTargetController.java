package com.chainsentinel.web.api;

import com.chainsentinel.core.service.PricePullTargetService;
import com.chainsentinel.core.service.dto.PricePullTargetCreateCommand;
import com.chainsentinel.core.service.dto.PricePullTargetUpdateCommand;
import com.chainsentinel.core.service.dto.PricePullTargetView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/price-pull-targets")
@Validated
public class PricePullTargetController {

	private final PricePullTargetService pricePullTargetService;

	public PricePullTargetController(PricePullTargetService pricePullTargetService) {
		this.pricePullTargetService = pricePullTargetService;
	}

	@PostMapping
	public PricePullTargetView create(@RequestBody @Valid PricePullTargetRequest request) {
		try {
			return pricePullTargetService.create(toCreateCommand(request));
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	@PutMapping("/{id}")
	public PricePullTargetView update(@PathVariable("id") Long id, @RequestBody @Valid PricePullTargetRequest request) {
		try {
			return pricePullTargetService.update(id, toUpdateCommand(request));
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable("id") Long id) {
		try {
			pricePullTargetService.delete(id);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@PatchMapping("/{id}/enable")
	public PricePullTargetView enable(@PathVariable("id") Long id) {
		try {
			return pricePullTargetService.setEnabled(id, true);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@PatchMapping("/{id}/disable")
	public PricePullTargetView disable(@PathVariable("id") Long id) {
		try {
			return pricePullTargetService.setEnabled(id, false);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@GetMapping("/{id}")
	public PricePullTargetView get(@PathVariable("id") Long id) {
		try {
			return pricePullTargetService.get(id);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@GetMapping
	public List<PricePullTargetView> list(
		@RequestParam(name = "providerConfigId", required = false) Long providerConfigId,
		@RequestParam(name = "enabled", required = false) Boolean enabled,
		@RequestParam(name = "q", required = false) String keyword,
		@RequestParam(name = "limit", defaultValue = "100") int limit
	) {
		if (limit < 1 || limit > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 500");
		}
		return pricePullTargetService.list(providerConfigId, enabled, keyword, limit);
	}

	private PricePullTargetCreateCommand toCreateCommand(PricePullTargetRequest request) {
		return new PricePullTargetCreateCommand(
			request.assetId(),
			request.providerConfigId(),
			request.instType(),
			request.instId(),
			request.quoteSymbol(),
			request.enabled(),
			request.pollIntervalMs(),
			request.priority()
		);
	}

	private PricePullTargetUpdateCommand toUpdateCommand(PricePullTargetRequest request) {
		return new PricePullTargetUpdateCommand(
			request.assetId(),
			request.providerConfigId(),
			request.instType(),
			request.instId(),
			request.quoteSymbol(),
			request.enabled(),
			request.pollIntervalMs(),
			request.priority()
		);
	}

	public record PricePullTargetRequest(
		@Min(1) Long assetId,
		@Min(1) Long providerConfigId,
		@NotBlank String instType,
		@NotBlank String instId,
		@NotBlank String quoteSymbol,
		Boolean enabled,
		Integer pollIntervalMs,
		@Min(0) Integer priority
	) {
		public PricePullTargetRequest {
			if (enabled == null) {
				enabled = true;
			}
			if (priority == null) {
				priority = 100;
			}
		}
	}
}