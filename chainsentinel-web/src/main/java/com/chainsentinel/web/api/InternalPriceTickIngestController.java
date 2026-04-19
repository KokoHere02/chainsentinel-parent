package com.chainsentinel.web.api;

import com.chainsentinel.infra.service.DbPriceTickBatchWriter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/price-ticks/ingest")
public class InternalPriceTickIngestController {

	private final DbPriceTickBatchWriter batchWriter;

	public InternalPriceTickIngestController(DbPriceTickBatchWriter batchWriter) {
		this.batchWriter = batchWriter;
	}

	@GetMapping("/status")
	public DbPriceTickBatchWriter.TickIngestStatus status() {
		return batchWriter.currentStatus();
	}
}