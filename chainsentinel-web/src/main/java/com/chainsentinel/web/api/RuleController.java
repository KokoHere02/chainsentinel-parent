package com.chainsentinel.web.api;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.rule.model.EventRuleSpec;
import com.chainsentinel.core.service.AlertRuleService;
import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
@Validated
public class RuleController {

    private final AlertRuleService alertRuleService;

    public RuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @PostMapping
    public AlertRuleView create(@RequestBody @Valid RuleCreateRequest request) {
        return alertRuleService.create(new AlertRuleCreateCommand(
                request.name(),
                request.type(),
                request.condition(),
                request.severity(),
                request.enabled()
        ));
    }

    public record RuleCreateRequest(
            @NotBlank String name,
            @NotNull AlertRuleType type,
            @NotNull EventRuleSpec condition,
            @NotBlank String severity,
            Boolean enabled
    ) {
        public RuleCreateRequest {
            if (enabled == null) {
                enabled = true;
            }
        }
    }
}
