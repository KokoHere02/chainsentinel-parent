package com.chainsentinel.web.api;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.service.EventQueryService;
import com.chainsentinel.core.service.dto.EventQuery;
import com.chainsentinel.core.service.dto.EventView;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

	private final EventQueryService eventQueryService;

	public EventController(EventQueryService eventQueryService) {
		this.eventQueryService = eventQueryService;
	}

	@GetMapping
	public Page<EventView> list(
		@RequestParam(name = "chain", required = false) String chain,
		@RequestParam(name = "address", required = false) String address,
		@RequestParam(name = "status", required = false) EventStatus status,
		@RequestParam(name = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
		@RequestParam(name = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
		@RequestParam(name = "page", defaultValue = "0") int page,
		@RequestParam(name = "size", defaultValue = "20") int size
	) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "blockNumber"));
		EventQuery query = new EventQuery(chain, address, status, startTime, endTime);
		return eventQueryService.query(query, pageable);
	}

	@GetMapping("/transfers")
	public Page<EventView> listTransfers(
		@RequestParam(name = "chain", required = false) String chain,
		@RequestParam(name = "network", required = false) String network,
		@RequestParam(name = "address", required = false) String address,
		@RequestParam(name = "fromAddress", required = false) String fromAddress,
		@RequestParam(name = "toAddress", required = false) String toAddress,
		@RequestParam(name = "symbol", required = false) String symbol,
		@RequestParam(name = "txHash", required = false) String txHash,
		@RequestParam(name = "status", required = false) EventStatus status,
		@RequestParam(name = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
		@RequestParam(name = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
		@RequestParam(name = "page", defaultValue = "0") int page,
		@RequestParam(name = "size", defaultValue = "20") int size
	) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "blockNumber"));
		EventQuery query = new EventQuery(
			chain,
			network,
			address,
			fromAddress,
			toAddress,
			symbol,
			txHash,
			status,
			startTime,
			endTime
		);
		return eventQueryService.query(query, pageable);
	}
}