package com.chainsentinel.web.api;

import com.chainsentinel.core.service.MonitorTreeQueryService;
import com.chainsentinel.core.service.dto.MonitorAddressTreeView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/monitor-tree")
@Validated
public class MonitorTreeController {

	private final MonitorTreeQueryService monitorTreeQueryService;

	public MonitorTreeController(MonitorTreeQueryService monitorTreeQueryService) {
		this.monitorTreeQueryService = monitorTreeQueryService;
	}

	@GetMapping
	public List<MonitorAddressTreeView> tree(
		@RequestParam(name = "enabledOnly", defaultValue = "true") boolean enabledOnly,
		@RequestParam(name = "limit", defaultValue = "200") int limit
	) {
		if (limit < 1 || limit > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 500");
		}
		return monitorTreeQueryService.tree(enabledOnly, limit);
	}
}

