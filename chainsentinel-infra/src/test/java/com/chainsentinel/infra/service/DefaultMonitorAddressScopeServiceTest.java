package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.dto.MonitorAddressScopeUpsertCommand;
import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import com.chainsentinel.infra.entity.MonitorScopeTokenEntity;
import com.chainsentinel.infra.repository.MonitorAddressScopeRepository;
import com.chainsentinel.infra.repository.MonitorScopeTokenRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultMonitorAddressScopeServiceTest {

	@Mock
	private MonitorAddressScopeRepository monitorAddressScopeRepository;
	@Mock
	private MonitorScopeTokenRepository monitorScopeTokenRepository;

	@Test
	void shouldDisableTokensWhenScopeDisabled() {
		MonitorAddressScopeEntity existing = new MonitorAddressScopeEntity();
		ReflectionTestUtils.setField(existing, "id", 11L);
		existing.setMonitorAddressId(1L);
		existing.setChain("ETH");
		existing.setNetwork("mainnet");
		existing.setEnabled(true);

		when(monitorAddressScopeRepository.findByMonitorAddressIdAndChainAndNetwork(1L, "ETH", "mainnet"))
			.thenReturn(Optional.of(existing));
		when(monitorAddressScopeRepository.save(any(MonitorAddressScopeEntity.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		MonitorScopeTokenEntity token = new MonitorScopeTokenEntity();
		ReflectionTestUtils.setField(token, "id", 101L);
		token.setMonitorScopeId(11L);
		token.setEnabled(true);
		when(monitorScopeTokenRepository.findByMonitorScopeId(11L)).thenReturn(List.of(token));

		DefaultMonitorAddressScopeService service = new DefaultMonitorAddressScopeService(
			monitorAddressScopeRepository,
			monitorScopeTokenRepository
		);
		service.upsert(new MonitorAddressScopeUpsertCommand(1L, "eth", "MainNet", false));

		assertFalse(existing.getEnabled());
		assertFalse(token.getEnabled());
		verify(monitorScopeTokenRepository).saveAll(List.of(token));
	}

	@Test
	void shouldKeepTokensWhenScopeEnabled() {
		when(monitorAddressScopeRepository.findByMonitorAddressIdAndChainAndNetwork(1L, "ETH", "mainnet"))
			.thenReturn(Optional.empty());
		MonitorAddressScopeEntity saved = new MonitorAddressScopeEntity();
		ReflectionTestUtils.setField(saved, "id", 22L);
		saved.setEnabled(true);
		when(monitorAddressScopeRepository.save(any(MonitorAddressScopeEntity.class))).thenReturn(saved);

		DefaultMonitorAddressScopeService service = new DefaultMonitorAddressScopeService(
			monitorAddressScopeRepository,
			monitorScopeTokenRepository
		);
		boolean enabled = service.upsert(new MonitorAddressScopeUpsertCommand(1L, "ETH", "mainnet", true)).enabled();
		assertTrue(enabled);
	}
}

