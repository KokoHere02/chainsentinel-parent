package com.chainsentinel.infra.service;

import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.projection.AlertFailureSummaryProjection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AlertFailureSummaryService {

	private final AlertEventRepository alertEventRepository;

	public AlertFailureSummaryService(AlertEventRepository alertEventRepository) {
		this.alertEventRepository = alertEventRepository;
	}

	public AlertFailureSummaryView summarize() {
		List<FailureItem> last24h = toItems(alertEventRepository.summarizeFailuresSince(Instant.now().minus(24, ChronoUnit.HOURS)));
		List<FailureItem> last7d = toItems(alertEventRepository.summarizeFailuresSince(Instant.now().minus(7, ChronoUnit.DAYS)));
		return new AlertFailureSummaryView(last24h, last7d, Instant.now());
	}

	private List<FailureItem> toItems(List<AlertFailureSummaryProjection> rows) {
		return rows.stream()
			.map(row -> new FailureItem(
				row.getSendStatus(),
				row.getLastError() == null ? "(none)" : row.getLastError(),
				row.getFailureCount()
			))
			.sorted(Comparator.comparingLong(FailureItem::count).reversed())
			.toList();
	}

	public record AlertFailureSummaryView(
		List<FailureItem> last24h,
		List<FailureItem> last7d,
		Instant generatedAt
	) {
	}

	public record FailureItem(
		String sendStatus,
		String lastError,
		Long count
	) {
	}
}
