package com.chainsentinel.web.api;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.service.AlertRuleService;
import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleQueryCommand;
import com.chainsentinel.core.service.dto.AlertRuleUpdateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

	@GetMapping
	public List<AlertRuleView> list(
		@RequestParam(required = false) AlertRuleType type,
		@RequestParam(required = false) Boolean enabled,
		@RequestParam(required = false) String keyword
	) {
		return alertRuleService.list(new AlertRuleQueryCommand(type, enabled, keyword));
	}

	@PutMapping("/{id}")
	public AlertRuleView update(@PathVariable Long id, @RequestBody @Valid RuleUpdateRequest request) {
		return alertRuleService.update(new AlertRuleUpdateCommand(
			id,
			request.name(),
			request.condition(),
			request.severity(),
			request.enabled()
		));
	}

	@DeleteMapping("/{id}")
	public AlertRuleView delete(@PathVariable Long id) {
		return alertRuleService.delete(id);
	}

	public record RuleCreateRequest(
		@NotBlank String name,
		@NotNull AlertRuleType type,
		@NotNull JsonNode condition,
		@NotBlank String severity,
		Boolean enabled
	) {
		public RuleCreateRequest {
			if (enabled == null) {
				enabled = true;
			}
		}
	}

	public record RuleUpdateRequest(
		@NotBlank String name,
		@NotNull JsonNode condition,
		@NotBlank String severity,
		Boolean enabled
	) {
		public RuleUpdateRequest {
			if (enabled == null) {
				enabled = true;
			}
		}
	}
}
