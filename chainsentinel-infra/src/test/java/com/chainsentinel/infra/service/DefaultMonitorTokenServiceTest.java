package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.dto.MonitorTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorTokenView;
import com.chainsentinel.infra.entity.MonitorTokenEntity;
import com.chainsentinel.infra.repository.MonitorTokenRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultMonitorTokenServiceTest {

    @Mock
    private MonitorTokenRepository monitorTokenRepository;

    @Test
    void shouldCreateNewTokenWhenNotExist() {
        when(monitorTokenRepository.findByChainAndTokenContract("ETH", "0xabc")).thenReturn(Optional.empty());
        when(monitorTokenRepository.save(any(MonitorTokenEntity.class))).thenAnswer(invocation -> {
            MonitorTokenEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 2L);
            return entity;
        });

        DefaultMonitorTokenService service = new DefaultMonitorTokenService(monitorTokenRepository);
        MonitorTokenUpsertCommand command = new MonitorTokenUpsertCommand("eth", "0xAbC", "LINK", true);

        MonitorTokenView view = service.upsert(command);

        assertEquals(2L, view.id());
        assertEquals("ETH", view.chain());
        assertEquals("0xabc", view.tokenContract());
        assertEquals("LINK", view.symbol());
        assertTrue(view.enabled());
    }

    @Test
    void shouldDisableExistingToken() {
        MonitorTokenEntity existing = new MonitorTokenEntity();
        ReflectionTestUtils.setField(existing, "id", 1L);
        existing.setChain("ETH");
        existing.setTokenContract("0xdef");
        existing.setEnabled(true);

        when(monitorTokenRepository.findByChainAndTokenContract("ETH", "0xdef")).thenReturn(Optional.of(existing));
        when(monitorTokenRepository.save(any(MonitorTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DefaultMonitorTokenService service = new DefaultMonitorTokenService(monitorTokenRepository);
        MonitorTokenUpsertCommand command = new MonitorTokenUpsertCommand("ETH", "0xDef", "USDC", false);

        MonitorTokenView view = service.upsert(command);

        assertEquals(1L, view.id());
        assertEquals("ETH", view.chain());
        assertEquals("0xdef", view.tokenContract());
        assertEquals("USDC", view.symbol());
        assertFalse(view.enabled());
    }
}
