package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.AlertDispatchService;
import com.chainsentinel.infra.config.AlertProperties;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.repository.AssetEventRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private final AlertProperties alertProperties;
    private final AlertEventRepository alertEventRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AssetEventRepository assetEventRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public WebhookAlertDispatchService(
            AlertProperties alertProperties,
            AlertEventRepository alertEventRepository,
            AlertRuleRepository alertRuleRepository,
            AssetEventRepository assetEventRepository
    ) {
        this.alertProperties = alertProperties;
        this.alertEventRepository = alertEventRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.assetEventRepository = assetEventRepository;
    }

    @Override
    @Transactional
    public int dispatchPending() {
        if (!alertProperties.isEnabled() || !StringUtils.hasText(alertProperties.getWebhookUrl())) {
            return 0;
        }
        List<AlertEventEntity> pending = alertEventRepository.findTop100BySendStatusOrderByIdAsc("PENDING");
        int success = 0;
        for (AlertEventEntity alert : pending) {
            if (doSend(alert)) {
                success++;
            }
        }
        return success;
    }

    @Override
    @Transactional
    public boolean retryOne(Long alertId) {
        Optional<AlertEventEntity> optional = alertEventRepository.findById(alertId);
        if (optional.isEmpty()) {
            return false;
        }
        AlertEventEntity alert = optional.get();
        if ("SENT".equalsIgnoreCase(alert.getSendStatus())) {
            return true;
        }
        if (alert.getRetryCount() != null && alert.getRetryCount() >= alertProperties.getRetryMax()) {
            alert.setSendStatus("FAILED");
            alertEventRepository.save(alert);
            return false;
        }
        return doSend(alert);
    }

    private boolean doSend(AlertEventEntity alert) {
        try {
            Map<String, Object> payload = buildPayload(alert);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    alertProperties.getWebhookUrl(),
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            if (response.getStatusCode().is2xxSuccessful()) {
                alert.setSendStatus("SENT");
                alert.setLastError(null);
                alert.setSentAt(Instant.now());
                alertEventRepository.save(alert);
                return true;
            }
            return markFailure(alert, "HTTP " + response.getStatusCode().value());
        } catch (RestClientException e) {
            return markFailure(alert, e.getMessage());
        }
    }

    private Map<String, Object> buildPayload(AlertEventEntity alert) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("alertId", alert.getId());
        payload.put("ruleId", alert.getRuleId());
        payload.put("assetEventId", alert.getAssetEventId());
        payload.put("severity", alert.getSeverity());

        alertRuleRepository.findById(alert.getRuleId()).ifPresent(rule -> {
            payload.put("ruleName", rule.getName());
            payload.put("ruleType", rule.getType().name());
        });

        assetEventRepository.findById(alert.getAssetEventId()).ifPresent(event -> {
            payload.put("chain", event.getChain());
            payload.put("network", event.getNetwork());
            payload.put("txHash", event.getTxHash());
            payload.put("from", event.getFromAddress());
            payload.put("to", event.getToAddress());
            payload.put("amount", event.getAmount());
            payload.put("tokenType", event.getTokenType().name());
            payload.put("occurredAt", event.getOccurredAt());
        });

        return payload;
    }

    private boolean markFailure(AlertEventEntity alert, String error) {
        int retry = alert.getRetryCount() == null ? 0 : alert.getRetryCount();
        retry++;
        alert.setRetryCount(retry);
        alert.setLastError(trimError(error));
        if (retry >= alertProperties.getRetryMax()) {
            alert.setSendStatus("FAILED");
        } else {
            alert.setSendStatus("PENDING");
        }
        alertEventRepository.save(alert);
        log.warn("Alert dispatch failed, id={}, retry={}, error={}", alert.getId(), retry, alert.getLastError());
        return false;
    }

    private String trimError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 1000 ? error.substring(0, 1000) : error;
    }
}

