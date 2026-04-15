package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.dto.ChainConfigUpsertCommand;
import com.chainsentinel.core.service.dto.ChainConfigView;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultChainConfigServiceTest {

	@Mock
	private ChainConfigRepository chainConfigRepository;

	@Mock
	private ChainConfigRpcUrlCodec chainConfigRpcUrlCodec;

	@Test
	void shouldEncryptRpcUrlBeforeSaveAndDecryptForResponse() {
		ChainConfigEntity existing = new ChainConfigEntity();
		ReflectionTestUtils.setField(existing, "id", 10L);
		existing.setChain("ETH");
		existing.setNetwork("sepolia");

		when(chainConfigRepository.findByChainAndNetwork("ETH", "sepolia")).thenReturn(Optional.of(existing));
		when(chainConfigRpcUrlCodec.encrypt("https://rpc.example")).thenReturn("v1:encrypted");
		when(chainConfigRpcUrlCodec.decryptIfNeeded("v1:encrypted", "ETH", "sepolia"))
			.thenReturn("https://rpc.example");
		when(chainConfigRepository.save(any(ChainConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		DefaultChainConfigService service = new DefaultChainConfigService(chainConfigRepository, chainConfigRpcUrlCodec);
		ChainConfigView view = service.upsert(new ChainConfigUpsertCommand(
			"ETH",
			"sepolia",
			"https://rpc.example",
			12,
			true
		));

		ArgumentCaptor<ChainConfigEntity> captor = ArgumentCaptor.forClass(ChainConfigEntity.class);
		verify(chainConfigRepository).save(captor.capture());
		ChainConfigEntity saved = captor.getValue();
		assertEquals("v1:encrypted", saved.getRpcUrl());

		assertEquals("https://rpc.example", view.rpcUrl());
		assertEquals("ETH", view.chain());
		assertEquals("sepolia", view.network());
	}

	@Test
	void shouldListWithDecryptedRpcUrl() {
		ChainConfigEntity first = new ChainConfigEntity();
		ReflectionTestUtils.setField(first, "id", 1L);
		first.setChain("BSC");
		first.setNetwork("mainnet");
		first.setRpcUrl("v1:bsc");
		first.setConfirmRequired(15);
		first.setEnabled(true);

		ChainConfigEntity second = new ChainConfigEntity();
		ReflectionTestUtils.setField(second, "id", 2L);
		second.setChain("ETH");
		second.setNetwork("sepolia");
		second.setRpcUrl("v1:eth");
		second.setConfirmRequired(12);
		second.setEnabled(false);

		when(chainConfigRepository.findAllByOrderByChainAscNetworkAsc()).thenReturn(List.of(first, second));
		when(chainConfigRpcUrlCodec.decryptIfNeeded("v1:bsc", "BSC", "mainnet")).thenReturn("https://bsc-rpc");
		when(chainConfigRpcUrlCodec.decryptIfNeeded("v1:eth", "ETH", "sepolia")).thenReturn("https://eth-rpc");

		DefaultChainConfigService service = new DefaultChainConfigService(chainConfigRepository, chainConfigRpcUrlCodec);
		List<ChainConfigView> result = service.list();

		assertEquals(2, result.size());
		assertEquals("https://bsc-rpc", result.get(0).rpcUrl());
		assertEquals("https://eth-rpc", result.get(1).rpcUrl());
		assertFalse(result.get(1).enabled());
	}

	@Test
	void shouldFindSingleConfigWithDecryptedRpcUrl() {
		ChainConfigEntity entity = new ChainConfigEntity();
		ReflectionTestUtils.setField(entity, "id", 3L);
		entity.setChain("ETH");
		entity.setNetwork("mainnet");
		entity.setRpcUrl("v1:main");
		entity.setConfirmRequired(12);
		entity.setEnabled(true);

		when(chainConfigRepository.findByChainAndNetwork("ETH", "mainnet")).thenReturn(Optional.of(entity));
		when(chainConfigRpcUrlCodec.decryptIfNeeded("v1:main", "ETH", "mainnet")).thenReturn("https://main-rpc");

		DefaultChainConfigService service = new DefaultChainConfigService(chainConfigRepository, chainConfigRpcUrlCodec);
		Optional<ChainConfigView> result = service.find("ETH", "mainnet");

		assertTrue(result.isPresent());
		assertEquals("https://main-rpc", result.get().rpcUrl());
		assertEquals(12, result.get().confirmRequired());
	}

	@Test
	void shouldDeleteWhenConfigExists() {
		ChainConfigEntity entity = new ChainConfigEntity();
		entity.setChain("ETH");
		entity.setNetwork("mainnet");

		when(chainConfigRepository.findByChainAndNetwork("ETH", "mainnet")).thenReturn(Optional.of(entity));

		DefaultChainConfigService service = new DefaultChainConfigService(chainConfigRepository, chainConfigRpcUrlCodec);
		boolean deleted = service.delete("ETH", "mainnet");

		assertTrue(deleted);
		verify(chainConfigRepository).delete(entity);
	}

	@Test
	void shouldSetEnabledWhenConfigExists() {
		ChainConfigEntity entity = new ChainConfigEntity();
		ReflectionTestUtils.setField(entity, "id", 9L);
		entity.setChain("ETH");
		entity.setNetwork("mainnet");
		entity.setRpcUrl("v1:main");
		entity.setConfirmRequired(12);
		entity.setEnabled(true);

		when(chainConfigRepository.findByChainAndNetwork("ETH", "mainnet")).thenReturn(Optional.of(entity));
		when(chainConfigRepository.save(any(ChainConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(chainConfigRpcUrlCodec.decryptIfNeeded("v1:main", "ETH", "mainnet")).thenReturn("https://main-rpc");

		DefaultChainConfigService service = new DefaultChainConfigService(chainConfigRepository, chainConfigRpcUrlCodec);
		Optional<ChainConfigView> result = service.setEnabled("ETH", "mainnet", false);

		assertTrue(result.isPresent());
		assertFalse(result.get().enabled());
	}
}