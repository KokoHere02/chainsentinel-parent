package com.chainsentinel.web.api;

import com.chainsentinel.core.service.ChainConfigService;
import com.chainsentinel.core.service.dto.ChainConfigUpsertCommand;
import com.chainsentinel.core.service.dto.ChainConfigView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
