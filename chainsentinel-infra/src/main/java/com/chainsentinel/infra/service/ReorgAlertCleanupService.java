package com.chainsentinel.infra.service;

import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReorgAlertCleanupService {

	private static final Logger log = LoggerFactory.getLogger(ReorgAlertCleanupService.class);
	private static final String STATUS_PENDING = "PENDING";
	private static final String STATUS_FAILED = "FAILED";
	private static final String STATUS_CANCELED = "CANCELED";
	private static final String REORG_REASON = "asset event reorged";
	private static final String METRIC_ALERT_REORG_CLEANUP_TOTAL = "alert_reorg_cleanup_total";
	private static final List<String> CANCELABLE_STATUSES = List.of(STATUS_PENDING, STATUS_FAILED);

	private final AlertEventRepository alertEventRepository;
	private final MeterRegistry meterRegistry;

	public ReorgAlertCleanupService(AlertEventRepository alertEventRepository, MeterRegistry meterRegistry) {
		this.alertEventRepository = alertEventRepository;
		this.meterRegistry = meterRegistry;
	}

	@Transactional
	public int cancelPendingAlertsForReorgedEvents(List<Long> assetEventIds) {
		if (assetEventIds == null || assetEventIds.isEmpty()) {
			return 0;
		}

		List<AlertEventEntity> changed = new ArrayList<>();
		for (Long assetEventId : assetEventIds) {
			if (assetEventId == null) {
				continue;
			}
			List<AlertEventEntity> alerts = alertEventRepository.findByAssetEventIdAndSendStatusIn(
				assetEventId,
				CANCELABLE_STATUSES
			);
			for (AlertEventEntity alert : alerts) {
				alert.setSendStatus(STATUS_CANCELED);
				alert.setLastError(REORG_REASON);
				changed.add(alert);
			}
		}

		if (!changed.isEmpty()) {
			alertEventRepository.saveAll(changed);
			meterRegistry.counter(METRIC_ALERT_REORG_CLEANUP_TOTAL).increment(changed.size());
			log.warn("alert.reorg.cleanup canceledAlerts={} assetEventIds={}", changed.size(), assetEventIds.size());
		}
		return changed.size();
	}
}
