package com.chainsentinel.web.api;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.service.AlertRuleService;
import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleQueryCommand;
import com.chainsentinel.core.service.dto.AlertRuleUpdateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
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

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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

	@PostMapping("/from-template")
	public AlertRuleView createFromTemplate(@RequestBody @Valid RuleCreateFromTemplateRequest request) {
		RuleTemplateController.RuleTemplateView template = RuleTemplateController.findByKey(request.templateKey())
			.orElseThrow(() -> new IllegalArgumentException("Unknown templateKey: " + request.templateKey()));
		JsonNode condition = mergeTemplateCondition(template.condition(), request.conditionOverrides());
		return alertRuleService.create(new AlertRuleCreateCommand(
			request.name() == null ? template.name() : request.name(),
			template.type(),
			condition,
			request.severity() == null ? template.severity() : request.severity(),
			request.enabled() == null ? template.enabled() : request.enabled()
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

	@GetMapping("/{id}")
	public AlertRuleView getById(@PathVariable Long id) {
		return alertRuleService.getById(id);
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

	@PatchMapping("/{id}/enable")
	public AlertRuleView enable(@PathVariable Long id) {
		return alertRuleService.setEnabled(id, true);
	}

	@PatchMapping("/{id}/disable")
	public AlertRuleView disable(@PathVariable Long id) {
		return alertRuleService.setEnabled(id, false);
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

	public record RuleCreateFromTemplateRequest(
		@NotBlank String templateKey,
		String name,
		String severity,
		Boolean enabled,
		JsonNode conditionOverrides
	) {
	}

	private JsonNode mergeTemplateCondition(JsonNode templateCondition, JsonNode conditionOverrides) {
		if (conditionOverrides == null || conditionOverrides.isNull()) {
			return templateCondition.deepCopy();
		}
		if (!conditionOverrides.isObject()) {
			throw new IllegalArgumentException("conditionOverrides must be a JSON object");
		}
		ObjectNode merged = templateCondition.deepCopy();
		mergeObjectNode(merged, conditionOverrides);
		return merged;
	}

	private void mergeObjectNode(ObjectNode target, JsonNode source) {
		source.fields().forEachRemaining(entry -> {
			String fieldName = entry.getKey();
			JsonNode sourceValue = entry.getValue();
			JsonNode targetValue = target.get(fieldName);
			if (sourceValue.isObject() && targetValue != null && targetValue.isObject()) {
				mergeObjectNode((ObjectNode) targetValue, sourceValue);
				return;
			}
			target.set(fieldName, OBJECT_MAPPER.valueToTree(sourceValue));
		});
	}
}
