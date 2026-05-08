package com.chainsentinel.infra.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.rule.model.PriceRuleSpec;
import com.chainsentinel.core.service.AlertDispatchService;
import com.chainsentinel.infra.config.AlertProperties;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.repository.AssetEventRepository;
import com.chainsentinel.infra.repository.AssetPriceSnapshotRepository;
import com.chainsentinel.infra.rule.RuleConditionJsonParser;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class WebhookAlertDispatchService implements AlertDispatchService {

	private static final Logger log = LoggerFactory.getLogger(WebhookAlertDispatchService.class);

	private static final String STATUS_PENDING = "PENDING";
	private static final String STATUS_SENT = "SENT";
	private static final String STATUS_FAILED = "FAILED";
	private static final String STATUS_CANCELED = "CANCELED";
	private static final String METRIC_ALERT_SEND_TOTAL = "alert_send_total";
	private static final String METRIC_ALERT_SEND_SUCCESS_TOTAL = "alert_send_success_total";
	private static final String METRIC_ALERT_RETRY_TOTAL = "alert_retry_total";

	private final AlertProperties alertProperties;
	private final AlertEventRepository alertEventRepository;
	private final AlertRuleRepository alertRuleRepository;
	private final AssetEventRepository assetEventRepository;
	private final AssetPriceSnapshotRepository assetPriceSnapshotRepository;
	private final RuleConditionJsonParser ruleConditionJsonParser;
	private final MeterRegistry meterRegistry;
	private final AtomicLong alertSendTotal = new AtomicLong();
	private final AtomicLong alertSendSuccessTotal = new AtomicLong();
	private final RestTemplate restTemplate = new RestTemplate();
	private final ConcurrentHashMap<Long, ReentrantLock> retryLocks = new ConcurrentHashMap<>();

	public WebhookAlertDispatchService(
		AlertProperties alertProperties,
		AlertEventRepository alertEventRepository,
		AlertRuleRepository alertRuleRepository,
		AssetEventRepository assetEventRepository,
		AssetPriceSnapshotRepository assetPriceSnapshotRepository,
		RuleConditionJsonParser ruleConditionJsonParser,
		MeterRegistry meterRegistry
	) {
		this.alertProperties = alertProperties;
		this.alertEventRepository = alertEventRepository;
		this.alertRuleRepository = alertRuleRepository;
		this.assetEventRepository = assetEventRepository;
		this.assetPriceSnapshotRepository = assetPriceSnapshotRepository;
		this.ruleConditionJsonParser = ruleConditionJsonParser;
		this.meterRegistry = meterRegistry;
		meterRegistry.gauge("alert_send_success_rate", this, s -> {
			long total = s.alertSendTotal.get();
			if (total <= 0) {
				return 0.0;
			}
			return (double) s.alertSendSuccessTotal.get() / total;
		});
	}

	@Override
	@Transactional
	public int dispatchPending() {
		if (!alertProperties.isEnabled() || !StringUtils.hasText(alertProperties.getWebhookUrl())) {
			log.debug("alert.dispatch.batch.skip enabled={} webhookConfigured={}",
				alertProperties.isEnabled(),
				StringUtils.hasText(alertProperties.getWebhookUrl()));
			return 0;
		}

		List<AlertEventEntity> pending = alertEventRepository.findTop100BySendStatusOrderByIdAsc(STATUS_PENDING);
		if (pending.isEmpty()) {
			log.debug("alert.dispatch.batch.empty");
			return 0;
		}

		int success = 0;
		for (AlertEventEntity alert : pending) {
			if (doSend(alert)) {
				success++;
			}
		}

		log.info("alert.dispatch.batch.done fetched={} sent={} failed={}", pending.size(), success,
			pending.size() - success);
		return success;
	}

	@Override
	@Transactional
	public boolean retryOne(Long alertId) {
		if (!tryAcquireRetryLock(alertId)) {
			log.warn("alert.dispatch.retry.skip_locked alertId={}", alertId);
			return false;
		}

		try {
			Optional<AlertEventEntity> optional = alertEventRepository.findById(alertId);
			if (optional.isEmpty()) {
				log.warn("alert.dispatch.retry.not_found alertId={}", alertId);
				return false;
			}

			AlertEventEntity alert = optional.get();
			if (STATUS_SENT.equalsIgnoreCase(alert.getSendStatus())) {
				log.info("alert.dispatch.retry.skip_sent alertId={}", alertId);
				return true;
			}

			if (alert.getRetryCount() != null && alert.getRetryCount() >= alertProperties.getRetryMax()) {
				alert.setSendStatus(STATUS_FAILED);
				alertEventRepository.save(alert);
				log.warn("alert.dispatch.retry.force_failed alertId={} retry={} retryMax={}",
					alert.getId(), alert.getRetryCount(), alertProperties.getRetryMax());
				return false;
			}

			boolean ok = doSend(alert);
			log.info("alert.dispatch.retry.done alertId={} sent={}", alertId, ok);
			return ok;
		} finally {
			releaseRetryLock(alertId);
		}
	}

	private boolean doSend(AlertEventEntity alert) {
		Optional<AssetEventEntity> assetEvent = loadAssetEvent(alert);
		if (assetEvent.isPresent() && assetEvent.get().getStatus() == com.chainsentinel.core.model.EventStatus.REORGED) {
			alert.setSendStatus(STATUS_CANCELED);
			alert.setLastError("asset event reorged");
			alertEventRepository.save(alert);
			log.warn("alert.dispatch.skip_reorged alertId={} assetEventId={}", alert.getId(), alert.getAssetEventId());
			return false;
		}

		alertSendTotal.incrementAndGet();
		meterRegistry.counter(METRIC_ALERT_SEND_TOTAL).increment();
		try {
			Map<String, Object> payload = buildPayload(alert, assetEvent);
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			ResponseEntity<String> response = restTemplate.postForEntity(
				alertProperties.getWebhookUrl(),
				new HttpEntity<>(payload, headers),
				String.class
			);

			if (response.getStatusCode().is2xxSuccessful()) {
				alert.setSendStatus(STATUS_SENT);
				alert.setLastError(null);
				alert.setSentAt(Instant.now());
				alertEventRepository.save(alert);
				alertSendSuccessTotal.incrementAndGet();
				meterRegistry.counter(METRIC_ALERT_SEND_SUCCESS_TOTAL).increment();
				log.info("alert.dispatch.send.success alertId={} statusCode={}",
					alert.getId(), response.getStatusCode().value());
				return true;
			}

			return markFailure(alert, "HTTP " + response.getStatusCode().value());
		} catch (RestClientException e) {
			return markFailure(alert, e.getMessage());
		}
	}

	private Map<String, Object> buildPayload(AlertEventEntity alert, Optional<AssetEventEntity> assetEvent) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("alertId", alert.getId());
		payload.put("ruleId", alert.getRuleId());
		payload.put("assetEventId", alert.getAssetEventId());
		payload.put("severity", alert.getSeverity());

		alertRuleRepository.findById(alert.getRuleId()).ifPresent(rule -> {
			payload.put("ruleName", rule.getName());
			payload.put("ruleType", rule.getType().name());
			if (rule.getType() == AlertRuleType.PRICE_THRESHOLD) {
				enrichPricePayload(payload, rule.getConditionJson());
			}
		});

		assetEvent.ifPresent(event -> {
			payload.put("chain", event.getChain());
			payload.put("network", event.getNetwork());
			payload.put("txHash", event.getTxHash());
			payload.put("from", event.getFromAddress());
			payload.put("to", event.getToAddress());
			payload.put("amount", event.getAmount());
			if (event.getTokenType() != null) {
				payload.put("tokenType", event.getTokenType().name());
			}
			payload.put("occurredAt", event.getOccurredAt());
		});

		return payload;
	}

	private Optional<AssetEventEntity> loadAssetEvent(AlertEventEntity alert) {
		if (alert.getAssetEventId() == null) {
			return Optional.empty();
		}
		return assetEventRepository.findById(alert.getAssetEventId());
	}

	private void enrichPricePayload(Map<String, Object> payload, String conditionJson) {
		try {
			PriceRuleSpec spec = ruleConditionJsonParser.parsePrice(conditionJson);
			String symbol = spec.getCondition().getSymbol();
			payload.put("symbol", symbol);
			payload.put("op", spec.getCondition().getOp().wireValue());
			payload.put("threshold", spec.getCondition().getThreshold());
			payload.put("triggerMode", "ONCE");

			assetPriceSnapshotRepository.findTopByInstIdOrderByBucketTsDesc(symbol).ifPresent(snapshot -> {
				payload.put("currentPrice", snapshot.getPrice());
				payload.put("priceBucketTs", snapshot.getBucketTs());
			});
		} catch (Exception ex) {
			log.warn("alert.dispatch.price_payload_enrich_failed error={}", ex.getMessage());
		}
	}

	private boolean markFailure(AlertEventEntity alert, String error) {
		int retry = alert.getRetryCount() == null ? 0 : alert.getRetryCount();
		retry++;

		alert.setRetryCount(retry);
		alert.setLastError(trimError(error));
		meterRegistry.counter(METRIC_ALERT_RETRY_TOTAL).increment();

		String nextStatus = retry >= alertProperties.getRetryMax() ? STATUS_FAILED : STATUS_PENDING;
		alert.setSendStatus(nextStatus);
		alertEventRepository.save(alert);

		log.warn("alert.dispatch.send.failed alertId={} retry={} retryMax={} nextStatus={} error={}",
			alert.getId(), retry, alertProperties.getRetryMax(), nextStatus, alert.getLastError());
		return false;
	}

	private String trimError(String error) {
		if (error == null) {
			return null;
		}
		return error.length() > 1000 ? error.substring(0, 1000) : error;
	}

	private boolean tryAcquireRetryLock(Long alertId) {
		if (alertId == null) {
			return false;
		}
		ReentrantLock lock = retryLocks.computeIfAbsent(alertId, id -> new ReentrantLock());
		return lock.tryLock();
	}

	private void releaseRetryLock(Long alertId) {
		ReentrantLock lock = retryLocks.get(alertId);
		if (lock == null) {
			return;
		}
		try {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		} finally {
			if (!lock.isLocked()) {
				retryLocks.remove(alertId, lock);
			}
		}
	}

}
