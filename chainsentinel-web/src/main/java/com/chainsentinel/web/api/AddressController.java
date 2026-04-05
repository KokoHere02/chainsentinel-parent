package com.chainsentinel.web.api;

import com.chainsentinel.core.service.MonitorAddressService;
import com.chainsentinel.core.service.dto.MonitorAddressUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorAddressView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
		request.chain(),
		request.address(),
		request.tag(),
		request.enabled()
		));
	}

	public record AddressUpsertRequest(
	@NotBlank String chain,
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
