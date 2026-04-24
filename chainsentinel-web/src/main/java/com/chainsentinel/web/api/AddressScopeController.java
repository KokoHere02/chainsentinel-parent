package com.chainsentinel.web.api;

import com.chainsentinel.core.service.MonitorAddressScopeService;
import com.chainsentinel.core.service.dto.MonitorAddressScopeUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorAddressScopeView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/address-scopes")
@Validated
public class AddressScopeController {

	private final MonitorAddressScopeService monitorAddressScopeService;

	public AddressScopeController(MonitorAddressScopeService monitorAddressScopeService) {
		this.monitorAddressScopeService = monitorAddressScopeService;
	}

	@PostMapping
	public MonitorAddressScopeView upsert(@RequestBody @Valid AddressScopeUpsertRequest request) {
		return monitorAddressScopeService.upsert(new MonitorAddressScopeUpsertCommand(
			request.monitorAddressId(),
			request.chain(),
			request.network(),
			request.enabled()
		));
	}

	@GetMapping
	public List<MonitorAddressScopeView> list(
		@RequestParam(name = "monitorAddressId", required = false) Long monitorAddressId,
		@RequestParam(name = "chain", required = false) String chain,
		@RequestParam(name = "network", required = false) String network,
		@RequestParam(name = "enabled", required = false) Boolean enabled,
		@RequestParam(name = "limit", defaultValue = "50") int limit
	) {
		if (limit < 1 || limit > 200) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 200");
		}
		return monitorAddressScopeService.list(monitorAddressId, chain, network, enabled, limit);
	}

	public record AddressScopeUpsertRequest(
		@NotNull @Min(1) Long monitorAddressId,
		@NotBlank String chain,
		@NotBlank String network,
		Boolean enabled
	) {
		public AddressScopeUpsertRequest {
			if (enabled == null) {
				enabled = true;
			}
		}
	}
}

