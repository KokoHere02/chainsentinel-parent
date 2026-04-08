package com.chainsentinel.infra.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.chainsentinel.core.service.AlertDispatchService;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AlertRetryService {

	private static final Logger log = LoggerFactory.getLogger(AlertRetryService.class);

	private static final int MAX_LIMIT = 500;
	private static final List<String> RETRY_STATUSES = List.of("FAILED", "PENDING");

	private final AlertEventRepository alertEventRepository;
	private final AlertDispatchService alertDispatchService;

	public AlertRetryService(
		AlertEventRepository alertEventRepository,
		AlertDispatchService alertDispatchService
	) {
		this.alertEventRepository = alertEventRepository;
		this.alertDispatchService = alertDispatchService;
	}

	public BatchRetryResult retryFailed(int limit) {
		int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
		List<AlertEventEntity> alerts = alertEventRepository.findBySendStatusInOrderByIdAsc(
			RETRY_STATUSES,
			PageRequest.of(0, safeLimit)
		);

		int success = 0;
		int skipped = 0;
		List<Long> failedAlertIds = new ArrayList<>();

		for (AlertEventEntity alert : alerts) {
			Long alertId = alert.getId();
			if (alertId == null) {
				skipped++;
				continue;
			}

			boolean ok = alertDispatchService.retryOne(alertId);
			if (ok) {
				success++;
			}
			else {
				failedAlertIds.add(alertId);
			}
		}

		BatchRetryResult result = new BatchRetryResult(
			alerts.size(),
			success,
			failedAlertIds.size(),
			skipped,
			failedAlertIds,
			Instant.now()
		);

		log.info("alert.retry.batch.done total={} success={} failed={} skipped={}",
			result.total(), result.success(), result.failed(), result.skipped());
		return result;
	}

	public record BatchRetryResult(
		int total,
		int success,
		int failed,
		int skipped,
		List<Long> failedAlertIds,
		Instant retriedAt
	) {
	}

}
