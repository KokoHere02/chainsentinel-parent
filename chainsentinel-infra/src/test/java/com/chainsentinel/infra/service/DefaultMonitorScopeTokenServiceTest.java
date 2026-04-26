package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.dto.MonitorScopeTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorScopeTokenView;
import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import com.chainsentinel.infra.entity.MonitorScopeTokenEntity;
import com.chainsentinel.infra.repository.MonitorAddressScopeRepository;
import com.chainsentinel.infra.repository.MonitorScopeTokenRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultMonitorScopeTokenServiceTest {

	@Mock
	private MonitorScopeTokenRepository monitorScopeTokenRepository;

	@Mock
	private MonitorAddressScopeRepository monitorAddressScopeRepository;

	@InjectMocks
	private DefaultMonitorScopeTokenService service;

	@Test
	void shouldPreserveCaseForSolanaMint() {
		Long scopeId = 5L;
		String mint = "Aa6Fzr3u2gpxJLrKenPzq2mSwcfBVMPYdPb14S5fEPbQ";

		MonitorAddressScopeEntity scope = new MonitorAddressScopeEntity();
		scope.setChain("SOL");
		when(monitorAddressScopeRepository.findById(scopeId)).thenReturn(Optional.of(scope));
		when(monitorScopeTokenRepository.findByMonitorScopeIdAndTokenContract(scopeId, mint)).thenReturn(Optional.empty());
		when(monitorScopeTokenRepository.save(any(MonitorScopeTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		MonitorScopeTokenView view = service.upsert(new MonitorScopeTokenUpsertCommand(
			scopeId,
			mint,
			"TOKEN",
			9,
			true
		));

		assertEquals(mint, view.tokenContract());
	}

	@Test
	void shouldLowercaseTokenContractForEvmChain() {
		Long scopeId = 6L;
		String input = "0xAbCdEf1234";

		MonitorAddressScopeEntity scope = new MonitorAddressScopeEntity();
		scope.setChain("ETH");
		when(monitorAddressScopeRepository.findById(scopeId)).thenReturn(Optional.of(scope));
		when(monitorScopeTokenRepository.findByMonitorScopeIdAndTokenContract(scopeId, input.toLowerCase())).thenReturn(Optional.empty());
		when(monitorScopeTokenRepository.save(any(MonitorScopeTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		MonitorScopeTokenView view = service.upsert(new MonitorScopeTokenUpsertCommand(
			scopeId,
			input,
			"USDT",
			6,
			true
		));

		assertEquals(input.toLowerCase(), view.tokenContract());
	}

	@Test
	void shouldRejectInvalidSolanaMintFormat() {
		Long scopeId = 7L;
		String invalidMint = "aa6fzr3u2gpxjlrkenpzq2mswcfbvmpydpb14s5fepbq";

		MonitorAddressScopeEntity scope = new MonitorAddressScopeEntity();
		scope.setChain("SOL");
		when(monitorAddressScopeRepository.findById(scopeId)).thenReturn(Optional.of(scope));

		assertThrows(IllegalArgumentException.class, () -> service.upsert(new MonitorScopeTokenUpsertCommand(
			scopeId,
			invalidMint,
			"TOKEN",
			9,
			true
		)));
	}

	@Test
	void shouldAllowNativeTokenForSolanaScope() {
		Long scopeId = 8L;

		MonitorAddressScopeEntity scope = new MonitorAddressScopeEntity();
		scope.setChain("SOL");
		when(monitorAddressScopeRepository.findById(scopeId)).thenReturn(Optional.of(scope));
		when(monitorScopeTokenRepository.findByMonitorScopeIdAndTokenContract(scopeId, "NATIVE")).thenReturn(Optional.empty());
		when(monitorScopeTokenRepository.save(any(MonitorScopeTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		MonitorScopeTokenView view = service.upsert(new MonitorScopeTokenUpsertCommand(
			scopeId,
			"NATIVE",
			"SOL",
			9,
			true
		));

		assertEquals("NATIVE", view.tokenContract());
	}
}
