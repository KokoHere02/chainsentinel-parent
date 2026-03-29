package com.chainsentinel.web.api;

import com.chainsentinel.core.service.MonitorTokenService;
import com.chainsentinel.core.service.dto.MonitorTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorTokenView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
