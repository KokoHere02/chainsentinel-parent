package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AddressAlertMatcherTest {

    @Mock
    private MonitorAddressRepository monitorAddressRepository;

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Mock
    private AlertEventRepository alertEventRepository;

    private AddressAlertMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new AddressAlertMatcher(monitorAddressRepository, alertRuleRepository, alertEventRepository);
    }

    @Test
    void shouldIgnoreEventWithoutId() {
        AssetEventEntity event = new AssetEventEntity();
        event.setChain("ETH");
        event.setFromAddress("0xabc");
        event.setToAddress("0xdef");

        matcher.evaluate(event);

        verifyNoInteractions(monitorAddressRepository, alertRuleRepository, alertEventRepository);
    }

    @Test
    void shouldSkipWhenNoMonitoredAddressMatched() {
        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 10L);
        event.setChain("ETH");
        event.setFromAddress("0xABc");
        event.setToAddress("0xDeF");

        when(monitorAddressRepository.existsByChainAndAddressAndEnabledTrue("ETH", "0xabc")).thenReturn(false);
        when(monitorAddressRepository.existsByChainAndAddressAndEnabledTrue("ETH", "0xdef")).thenReturn(false);

        matcher.evaluate(event);

        verify(alertRuleRepository, never()).findByTypeAndEnabledTrue(AlertRuleType.ADDRESS);
        verify(alertEventRepository, never()).save(org.mockito.ArgumentMatchers.any(AlertEventEntity.class));
    }

    @Test
    void shouldCreateAlertsForUnsentRulesWhenAddressMatched() {
        AssetEventEntity event = new AssetEventEntity();
        ReflectionTestUtils.setField(event, "id", 99L);
        event.setChain("ETH");
        event.setFromAddress("0xAaa");
        event.setToAddress("0xbbb");

        AlertRuleEntity duplicated = new AlertRuleEntity();
        ReflectionTestUtils.setField(duplicated, "id", 1L);
        duplicated.setSeverity("HIGH");

        AlertRuleEntity newRule = new AlertRuleEntity();
        ReflectionTestUtils.setField(newRule, "id", 2L);
        newRule.setSeverity("LOW");

        when(monitorAddressRepository.existsByChainAndAddressAndEnabledTrue("ETH", "0xaaa")).thenReturn(true);
        when(alertRuleRepository.findByTypeAndEnabledTrue(AlertRuleType.ADDRESS)).thenReturn(List.of(duplicated, newRule));
        when(alertEventRepository.existsByRuleIdAndAssetEventId(1L, 99L)).thenReturn(true);
        when(alertEventRepository.existsByRuleIdAndAssetEventId(2L, 99L)).thenReturn(false);

        matcher.evaluate(event);

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(alertEventRepository).save(captor.capture());
        AlertEventEntity saved = captor.getValue();
        assertNotNull(saved);
        assertEquals(2L, saved.getRuleId());
        assertEquals(99L, saved.getAssetEventId());
        assertEquals("LOW", saved.getSeverity());
        assertEquals("PENDING", saved.getSendStatus());
        assertEquals(0, saved.getRetryCount());

        verify(monitorAddressRepository).existsByChainAndAddressAndEnabledTrue(eq("ETH"), eq("0xaaa"));
        verify(monitorAddressRepository).existsByChainAndAddressAndEnabledTrue(eq("ETH"), eq("0xbbb"));
    }
}
