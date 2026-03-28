package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.model.TokenType;
import com.chainsentinel.infra.config.AlertProperties;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.repository.AssetEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class WebhookAlertDispatchServiceTest {

    @Mock
    private AlertEventRepository alertEventRepository;

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Mock
    private AssetEventRepository assetEventRepository;

    @Mock
    private RestTemplate restTemplate;

    private AlertProperties alertProperties;
    private WebhookAlertDispatchService service;

    @BeforeEach
    void setUp() {
        alertProperties = new AlertProperties();
        alertProperties.setEnabled(true);
        alertProperties.setWebhookUrl("http://localhost/webhook");
        alertProperties.setRetryMax(3);

        service = new WebhookAlertDispatchService(alertProperties, alertEventRepository, alertRuleRepository, assetEventRepository);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
    }

    @Test
    void dispatchPendingShouldReturnZeroWhenDisabled() {
        alertProperties.setEnabled(false);

        int sent = service.dispatchPending();

        assertEquals(0, sent);
        verify(alertEventRepository, never()).findTop100BySendStatusOrderByIdAsc(anyString());
    }

    @Test
    void retryOneShouldReturnFalseWhenAlertNotFound() {
        when(alertEventRepository.findById(1L)).thenReturn(Optional.empty());

        assertFalse(service.retryOne(1L));

        verify(alertEventRepository, never()).save(any(AlertEventEntity.class));
    }

    @Test
    void retryOneShouldReturnTrueWhenAlreadySent() {
        AlertEventEntity alert = new AlertEventEntity();
        ReflectionTestUtils.setField(alert, "id", 1L);
        alert.setSendStatus("SENT");
        alert.setRetryCount(0);
        when(alertEventRepository.findById(1L)).thenReturn(Optional.of(alert));

        assertTrue(service.retryOne(1L));

        verify(alertEventRepository, never()).save(any(AlertEventEntity.class));
    }

    @Test
    void retryOneShouldMarkFailedWhenRetryReachedMax() {
        AlertEventEntity alert = new AlertEventEntity();
        ReflectionTestUtils.setField(alert, "id", 2L);
        alert.setSendStatus("PENDING");
        alert.setRetryCount(3);
        when(alertEventRepository.findById(2L)).thenReturn(Optional.of(alert));

        assertFalse(service.retryOne(2L));

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(alertEventRepository).save(captor.capture());
        assertEquals("FAILED", captor.getValue().getSendStatus());
    }

    @Test
    void dispatchPendingShouldMarkSentOnHttp2xx() {
        AlertEventEntity alert = new AlertEventEntity();
        ReflectionTestUtils.setField(alert, "id", 3L);
        alert.setRuleId(5L);
        alert.setAssetEventId(7L);
        alert.setSeverity("HIGH");
        alert.setSendStatus("PENDING");
        alert.setRetryCount(0);

        AlertRuleEntity rule = new AlertRuleEntity();
        ReflectionTestUtils.setField(rule, "id", 5L);
        rule.setName("r1");
        rule.setType(AlertRuleType.ADDRESS);

        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 7L);
        event.setChain("ETH");
        event.setNetwork("mainnet");
        event.setTxHash("0xhash");
        event.setFromAddress("0xfrom");
        event.setToAddress("0xto");
        event.setAmount(new BigDecimal("1"));
        event.setTokenType(TokenType.ETH);
        event.setOccurredAt(Instant.parse("2026-03-28T10:00:00Z"));

        when(alertEventRepository.findTop100BySendStatusOrderByIdAsc("PENDING")).thenReturn(List.of(alert));
        when(alertRuleRepository.findById(5L)).thenReturn(Optional.of(rule));
        when(assetEventRepository.findById(7L)).thenReturn(Optional.of(event));
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        int sent = service.dispatchPending();

        assertEquals(1, sent);
        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(alertEventRepository).save(captor.capture());
        AlertEventEntity saved = captor.getValue();
        assertEquals("SENT", saved.getSendStatus());
        assertNull(saved.getLastError());
        assertTrue(saved.getSentAt() != null);
    }
}
