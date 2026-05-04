package com.chainsentinel.web.api;

import com.chainsentinel.core.service.TradeAccountService;
import com.chainsentinel.core.service.TradeAccountAssetService;
import com.chainsentinel.core.service.dto.TradeAccountAssetSyncView;
import com.chainsentinel.core.service.dto.TradeAccountConnectivityTestView;
import com.chainsentinel.core.service.dto.TradeAccountBalanceSnapshotView;
import com.chainsentinel.core.service.dto.TradeAccountStreamStatusView;
import com.chainsentinel.core.service.dto.TradeAccountCreateCommand;
import com.chainsentinel.core.service.dto.TradePositionSnapshotView;
import com.chainsentinel.core.service.dto.TradeAccountUpdateCommand;
import com.chainsentinel.core.service.dto.TradeAccountView;
import com.chainsentinel.web.auth.AuthContext;
import com.chainsentinel.web.auth.AuthPrincipal;
import com.chainsentinel.web.auth.AuthRole;
import com.chainsentinel.web.auth.RequireRoles;
import jakarta.validation.Valid;
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
@RequestMapping("/api/trade/accounts")
@Validated
@RequireRoles(AuthRole.ADMIN)
public class TradeAccountController {

	private final TradeAccountService tradeAccountService;
	private final TradeAccountAssetService tradeAccountAssetService;

	public TradeAccountController(TradeAccountService tradeAccountService, TradeAccountAssetService tradeAccountAssetService) {
		this.tradeAccountService = tradeAccountService;
		this.tradeAccountAssetService = tradeAccountAssetService;
	}

	@PostMapping
	public TradeAccountView create(@RequestBody @Valid TradeAccountRequest request) {
		return tradeAccountService.create(toCreateCommand(request), currentUserId());
	}

	@PutMapping("/{id}")
	public TradeAccountView update(@PathVariable("id") Long id, @RequestBody @Valid TradeAccountRequest request) {
		try {
			return tradeAccountService.update(id, toUpdateCommand(request), currentUserId());
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable("id") Long id) {
		try {
			tradeAccountService.delete(id);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@GetMapping("/{id}")
	public TradeAccountView get(@PathVariable("id") Long id) {
		try {
			return tradeAccountService.get(id);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@GetMapping
	public List<TradeAccountView> list(
		@RequestParam(name = "enabled", required = false) Boolean enabled,
		@RequestParam(name = "provider", required = false) String provider,
		@RequestParam(name = "q", required = false) String keyword,
		@RequestParam(name = "limit", defaultValue = "100") int limit
	) {
		if (limit < 1 || limit > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 500");
		}
		return tradeAccountService.list(enabled, provider, keyword, limit);
	}

	@PatchMapping("/{id}/enable")
	public TradeAccountView enable(@PathVariable("id") Long id) {
		try {
			return tradeAccountService.setEnabled(id, true, currentUserId());
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@PatchMapping("/{id}/disable")
	public TradeAccountView disable(@PathVariable("id") Long id) {
		try {
			return tradeAccountService.setEnabled(id, false, currentUserId());
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@PostMapping("/{id}/test-connectivity")
	public TradeAccountConnectivityTestView testConnectivity(@PathVariable("id") Long id) {
		try {
			return tradeAccountService.testConnectivity(id);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@GetMapping("/ws-status")
	public List<TradeAccountStreamStatusView> streamStatuses() {
		return tradeAccountService.streamStatuses();
	}

	@GetMapping("/{id}/ws-status")
	public TradeAccountStreamStatusView streamStatus(@PathVariable("id") Long id) {
		try {
			return tradeAccountService.streamStatus(id);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@PostMapping("/{id}/sync-assets")
	public TradeAccountAssetSyncView syncAssets(@PathVariable("id") Long id) {
		try {
			return tradeAccountAssetService.sync(id, currentUserId());
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@GetMapping("/{id}/balances")
	public List<TradeAccountBalanceSnapshotView> balances(@PathVariable("id") Long id) {
		try {
			return tradeAccountAssetService.listLatestBalances(id);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	@GetMapping("/{id}/positions")
	public List<TradePositionSnapshotView> positions(@PathVariable("id") Long id) {
		try {
			return tradeAccountAssetService.listLatestPositions(id);
		} catch (NoSuchElementException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
		}
	}

	private Long currentUserId() {
		AuthPrincipal principal = AuthContext.get();
		return principal == null ? null : principal.userId();
	}

	private TradeAccountCreateCommand toCreateCommand(TradeAccountRequest request) {
		return new TradeAccountCreateCommand(
			request.name(),
			request.provider(),
			request.accountType(),
			request.envType(),
			request.apiKey(),
			request.apiSecret(),
			request.passphrase(),
			request.enabled(),
			request.remark()
		);
	}

	private TradeAccountUpdateCommand toUpdateCommand(TradeAccountRequest request) {
		return new TradeAccountUpdateCommand(
			request.name(),
			request.provider(),
			request.accountType(),
			request.envType(),
			request.apiKey(),
			request.apiSecret(),
			request.passphrase(),
			request.enabled(),
			request.remark()
		);
	}

	public record TradeAccountRequest(
		@NotBlank String name,
		@NotBlank String provider,
		String accountType,
		String envType,
		String apiKey,
		String apiSecret,
		String passphrase,
		Boolean enabled,
		String remark
	) {
		public TradeAccountRequest {
			if (accountType == null) {
				accountType = "API_KEY";
			}
			if (envType == null) {
				envType = "SIMULATED";
			}
			if (enabled == null) {
				enabled = true;
			}
		}
	}
}
