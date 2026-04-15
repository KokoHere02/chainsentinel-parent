package com.chainsentinel.web.api;

import com.chainsentinel.core.exception.NotFoundException;
import com.chainsentinel.core.service.ChainConfigService;
import com.chainsentinel.core.service.dto.ChainConfigUpsertCommand;
import com.chainsentinel.core.service.dto.ChainConfigView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chains")
@Validated
public class ChainController {

	private final ChainConfigService chainConfigService;

	public ChainController(ChainConfigService chainConfigService) {
		this.chainConfigService = chainConfigService;
	}

	@PostMapping
	public ChainConfigView upsert(@RequestBody @Valid ChainUpsertRequest request) {
		ChainConfigUpsertCommand command = new ChainConfigUpsertCommand(
			request.chain(),
			request.network(),
			request.rpcUrl(),
			request.confirmRequired(),
			request.enabled()
		);
		return chainConfigService.upsert(command);
	}

	@GetMapping
	public List<ChainConfigView> list() {
		return chainConfigService.list();
	}

	@GetMapping("/{chain}/{network}")
	public ChainConfigView get(@PathVariable String chain, @PathVariable String network) {
		return chainConfigService.find(chain, network)
			.orElseThrow(() -> new NotFoundException("chain config not found"));
	}

	@DeleteMapping("/{chain}/{network}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String chain, @PathVariable String network) {
		boolean deleted = chainConfigService.delete(chain, network);
		if (!deleted) {
			throw new NotFoundException("chain config not found");
		}
	}

	@PatchMapping("/{chain}/{network}/enable")
	public ChainConfigView enable(@PathVariable String chain, @PathVariable String network) {
		return chainConfigService.setEnabled(chain, network, true)
			.orElseThrow(() -> new NotFoundException("chain config not found"));
	}

	@PatchMapping("/{chain}/{network}/disable")
	public ChainConfigView disable(@PathVariable String chain, @PathVariable String network) {
		return chainConfigService.setEnabled(chain, network, false)
			.orElseThrow(() -> new NotFoundException("chain config not found"));
	}

	public record ChainUpsertRequest(
		@NotBlank String chain,
		@NotBlank String network,
		@NotBlank String rpcUrl,
		@Min(1) Integer confirmRequired,
		Boolean enabled
	) {
		public ChainUpsertRequest {
			if (confirmRequired == null) {
				confirmRequired = 12;
			}
			if (enabled == null) {
				enabled = true;
			}
		}
	}
}