package com.chainsentinel.web.api;

import com.chainsentinel.core.service.PriceProviderConfigService;
import com.chainsentinel.core.service.dto.PriceProviderConfigCreateCommand;
import com.chainsentinel.core.service.dto.PriceProviderConfigUpdateCommand;
import com.chainsentinel.core.service.dto.PriceProviderConfigView;
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
@RequestMapping("/api/price-provider-configs")
@Validated
public class PriceProviderConfigController {

	private final PriceProviderConfigService priceProviderConfigService;

	public PriceProviderConfigController(PriceProviderConfigService priceProviderConfigService) {
		this.priceProviderConfigService = priceProviderConfigService;
	}

	@PostMapping
	public PriceProviderConfigView create(@RequestBody @Valid PriceProviderConfigRequest request) {
		try {
			return priceProviderConfigService.create(toCreateCommand(request));
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	@PutMapping("/{id}")
	public PriceProviderConfigView update(@PathVariable("id") Long id, @RequestBody @Valid PriceProviderConfigRequest request) {
		try {
			return priceProviderConfigService.update(id, toUpdateCommand(request));
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable("id") Long id) {
		try {
			priceProviderConfigService.delete(id);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	@PatchMapping("/{id}/enable")
	public PriceProviderConfigView enable(@PathVariable("id") Long id) {
		try {
			return priceProviderConfigService.setEnabled(id, true);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@PatchMapping("/{id}/disable")
	public PriceProviderConfigView disable(@PathVariable("id") Long id) {
		try {
			return priceProviderConfigService.setEnabled(id, false);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@GetMapping("/{id}")
	public PriceProviderConfigView get(@PathVariable("id") Long id) {
		try {
			return priceProviderConfigService.get(id);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@GetMapping
	public List<PriceProviderConfigView> list(
		@RequestParam(name = "enabled", required = false) Boolean enabled,
		@RequestParam(name = "q", required = false) String keyword,
		@RequestParam(name = "limit", defaultValue = "100") int limit
	) {
		if (limit < 1 || limit > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 500");
		}
		return priceProviderConfigService.list(enabled, keyword, limit);
	}

	private PriceProviderConfigCreateCommand toCreateCommand(PriceProviderConfigRequest request) {
		return new PriceProviderConfigCreateCommand(
			request.providerName(),
			request.baseUrl(),
			request.enabled(),
			request.priority(),
			request.timeoutMs()
		);
	}

	private PriceProviderConfigUpdateCommand toUpdateCommand(PriceProviderConfigRequest request) {
		return new PriceProviderConfigUpdateCommand(
			request.providerName(),
			request.baseUrl(),
			request.enabled(),
			request.priority(),
			request.timeoutMs()
		);
	}

	public record PriceProviderConfigRequest(
		@NotBlank String providerName,
		@NotBlank String baseUrl,
		Boolean enabled,
		@Min(0) Integer priority,
		@Min(1) Integer timeoutMs
	) {
		public PriceProviderConfigRequest {
			if (enabled == null) {
				enabled = true;
			}
			if (priority == null) {
				priority = 100;
			}
			if (timeoutMs == null) {
				timeoutMs = 1500;
			}
		}
	}
}
