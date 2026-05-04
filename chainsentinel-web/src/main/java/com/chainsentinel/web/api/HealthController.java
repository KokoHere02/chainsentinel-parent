package com.chainsentinel.web.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.NamedContributors;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

	private final HealthEndpoint healthEndpoint;

	public HealthController(HealthEndpoint healthEndpoint) {
		this.healthEndpoint = healthEndpoint;
	}

	@GetMapping
	public ResponseEntity<Map<String, Object>> health() {
		return buildResponse(healthEndpoint.health());
	}

	@GetMapping("/readiness")
	public ResponseEntity<Map<String, Object>> readiness() {
		return buildResponse(healthEndpoint.healthForPath("readiness"));
	}

	@GetMapping("/liveness")
	public ResponseEntity<Map<String, Object>> liveness() {
		return buildResponse(healthEndpoint.healthForPath("liveness"));
	}

	private Map<String, Object> toBody(HealthComponent component) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", component.getStatus().getCode());
		if (component instanceof Health health && !health.getDetails().isEmpty()) {
			body.put("details", health.getDetails());
		}
		if (component instanceof NamedContributors<?> contributors) {
			Map<String, Object> children = new LinkedHashMap<>();
			contributors.forEach(contributor -> {
				Object child = contributor.getContributor();
				if (child instanceof HealthComponent healthComponent) {
					children.put(contributor.getName(), toBody(healthComponent));
				}
			});
			if (!children.isEmpty()) {
				body.put("components", children);
			}
		}
		return body;
	}

	private ResponseEntity<Map<String, Object>> buildResponse(HealthComponent component) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("name", "chainsentinel");
		payload.put("time", Instant.now().toString());
		payload.putAll(toBody(component));
		return ResponseEntity.status(resolveHttpStatus(component)).body(payload);
	}

	private HttpStatus resolveHttpStatus(HealthComponent component) {
		Status status = component.getStatus();
		if (Status.DOWN.equals(status) || Status.OUT_OF_SERVICE.equals(status)) {
			return HttpStatus.SERVICE_UNAVAILABLE;
		}
		return HttpStatus.OK;
	}
}
