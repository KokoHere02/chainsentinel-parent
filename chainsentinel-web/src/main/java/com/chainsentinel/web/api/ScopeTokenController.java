package com.chainsentinel.web.api;

import com.chainsentinel.core.service.MonitorScopeTokenService;
import com.chainsentinel.core.service.dto.MonitorScopeTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorScopeTokenView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/scope-tokens")
@Validated
public class ScopeTokenController {

	private final MonitorScopeTokenService monitorScopeTokenService;

	public ScopeTokenController(MonitorScopeTokenService monitorScopeTokenService) {
		this.monitorScopeTokenService = monitorScopeTokenService;
	}

	@PostMapping
	public MonitorScopeTokenView upsert(@RequestBody @Valid ScopeTokenUpsertRequest request) {
		return monitorScopeTokenService.upsert(new MonitorScopeTokenUpsertCommand(
			request.monitorScopeId(),
			request.tokenContract(),
			request.symbol(),
			request.decimals(),
			request.enabled()
		));
	}

	@GetMapping
	public List<MonitorScopeTokenView> list(
		@RequestParam(name = "monitorScopeId", required = false) Long monitorScopeId,
		@RequestParam(name = "q", required = false) String keyword,
		@RequestParam(name = "enabled", required = false) Boolean enabled,
		@RequestParam(name = "limit", defaultValue = "50") int limit
	) {
		if (limit < 1 || limit > 200) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 200");
		}
		return monitorScopeTokenService.list(monitorScopeId, keyword, enabled, limit);
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable @Min(1) Long id) {
		try {
			monitorScopeTokenService.delete(id);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	public record ScopeTokenUpsertRequest(
		@NotNull @Min(1) Long monitorScopeId,
		@NotBlank String tokenContract,
		String symbol,
		@Min(0) Integer decimals,
		Boolean enabled
	) {
		public ScopeTokenUpsertRequest {
			if (enabled == null) {
				enabled = true;
			}
		}
	}
}
