package com.chainsentinel.web.api;

import com.chainsentinel.core.service.MonitorAddressService;
import com.chainsentinel.core.service.dto.MonitorAddressUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorAddressView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/addresses")
@Validated
public class AddressController {

	private final MonitorAddressService monitorAddressService;

	public AddressController(MonitorAddressService monitorAddressService) {
		this.monitorAddressService = monitorAddressService;
	}

	@PostMapping
	public MonitorAddressView upsert(@RequestBody @Valid AddressUpsertRequest request) {
		return monitorAddressService.upsert(new MonitorAddressUpsertCommand(
			request.address(),
			request.tag(),
			request.enabled()
		));
	}

	@GetMapping
	public List<MonitorAddressView> list(
		@RequestParam(name = "q", required = false) String keyword,
		@RequestParam(name = "enabled", required = false) Boolean enabled,
		@RequestParam(name = "limit", defaultValue = "50") int limit
	) {
		if (limit < 1 || limit > 200) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 200");
		}
		return monitorAddressService.list(keyword, enabled, limit);
	}

	public record AddressUpsertRequest(
		@NotBlank String address,
		String tag,
		Boolean enabled
	) {
		public AddressUpsertRequest {
			if (enabled == null) {
				enabled = true;
			}
		}
	}
}
