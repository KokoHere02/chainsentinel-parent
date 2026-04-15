package com.chainsentinel.web.api;

import com.chainsentinel.core.service.MonitorTokenService;
import com.chainsentinel.core.service.dto.MonitorTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorTokenView;
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
@RequestMapping("/api/tokens")
@Validated
public class TokenController {

	private final MonitorTokenService monitorTokenService;

	public TokenController(MonitorTokenService monitorTokenService) {
		this.monitorTokenService = monitorTokenService;
	}

	@PostMapping
	public MonitorTokenView upsert(@RequestBody @Valid TokenUpsertRequest request) {
		return monitorTokenService.upsert(new MonitorTokenUpsertCommand(
			request.chain(),
			request.tokenContract(),
			request.symbol(),
			request.enabled()
		));
	}

	@GetMapping
	public List<MonitorTokenView> list(
		@RequestParam(name = "q", required = false) String keyword,
		@RequestParam(name = "chain", required = false) String chain,
		@RequestParam(name = "enabled", required = false) Boolean enabled,
		@RequestParam(name = "limit", defaultValue = "50") int limit
	) {
		if (limit < 1 || limit > 200) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 200");
		}
		return monitorTokenService.list(chain, keyword, enabled, limit);
	}

	public record TokenUpsertRequest(
		@NotBlank String chain,
		@NotBlank String tokenContract,
		String symbol,
		Boolean enabled
	) {
		public TokenUpsertRequest {
			if (enabled == null) {
				enabled = true;
			}
		}

	}

}