package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.entity.MonitorAddressEntity;
import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import com.chainsentinel.infra.repository.MonitorAddressScopeRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScannerOrchestratorServiceTest {

	@Mock
	private ChainConfigRepository chainConfigRepository;
	@Mock
	private MonitorAddressScopeRepository monitorAddressScopeRepository;
	@Mock
	private MonitorAddressRepository monitorAddressRepository;
	@Mock
	private ChainConfigRpcUrlCodec chainConfigRpcUrlCodec;
	@Mock
	private ChainEventScanner evmScanner;
	@Mock
	private ChainEventScanner solScanner;

	@Test
	void shouldDispatchByChainAndNetworkWithoutCrossChainMix() {
		MonitorAddressScopeEntity ethScope = scope(11L, 101L, "ETH", "mainnet", true);
		MonitorAddressScopeEntity solScope = scope(12L, 102L, "SOL", "mainnet", true);
		MonitorAddressScopeEntity bscScope = scope(13L, 103L, "BSC", "mainnet", true);
		when(monitorAddressScopeRepository.findByEnabledTrue()).thenReturn(List.of(ethScope, solScope, bscScope));

		String ethAddress = "0xABCDEFabcdefABCDEFabcdefABCDEFabcdef1234";
		String solAddress = "7kbnvuGBxxj8AG9qp8Scn56muWGaRaFqxg1FsRp3PaFT";
		String bscAddress = "0x3333333333333333333333333333333333333333";
		when(monitorAddressRepository.findByIdInAndEnabledTrue(any())).thenReturn(List.of(
			address(101L, ethAddress, true),
			address(102L, solAddress, true),
			address(103L, bscAddress, true)
		));

		ChainConfigEntity ethCfg = chain("ETH", "mainnet", "enc:eth");
		ChainConfigEntity solCfg = chain("SOL", "mainnet", "enc:sol");
		ChainConfigEntity bscCfg = chain("BSC", "mainnet", "enc:bsc");
		when(chainConfigRepository.findByEnabledTrue()).thenReturn(List.of(ethCfg, solCfg, bscCfg));

		when(chainConfigRpcUrlCodec.decryptIfNeeded("enc:eth", "ETH", "mainnet")).thenReturn("https://eth-rpc");
		when(chainConfigRpcUrlCodec.decryptIfNeeded("enc:sol", "SOL", "mainnet")).thenReturn("https://sol-rpc");
		when(chainConfigRpcUrlCodec.decryptIfNeeded("enc:bsc", "BSC", "mainnet")).thenReturn("https://bsc-rpc");

		when(evmScanner.supports("ETH")).thenReturn(true);
		when(evmScanner.supports("BSC")).thenReturn(true);
		when(solScanner.supports("ETH")).thenReturn(false);
		when(solScanner.supports("SOL")).thenReturn(true);
		when(solScanner.supports("BSC")).thenReturn(false);

		when(evmScanner.scan(any(), any())).thenReturn(2, 0);
		when(solScanner.scan(any(), any())).thenReturn(3);

		ScannerOrchestratorService service = new ScannerOrchestratorService(
			chainConfigRepository,
			monitorAddressScopeRepository,
			monitorAddressRepository,
			chainConfigRpcUrlCodec,
			List.of(solScanner, evmScanner)
		);

		int inserted = service.runOnce();

		assertEquals(5, inserted);
		verify(evmScanner, times(2)).scan(any(), any());
		verify(solScanner, times(1)).scan(any(), any());

		ArgumentCaptor<RuntimeWatchers> evmWatchersCaptor = ArgumentCaptor.forClass(RuntimeWatchers.class);
		ArgumentCaptor<RuntimeWatchers> solWatchersCaptor = ArgumentCaptor.forClass(RuntimeWatchers.class);
		ArgumentCaptor<ChainRuntimeConfig> evmRuntimeCaptor = ArgumentCaptor.forClass(ChainRuntimeConfig.class);
		ArgumentCaptor<ChainRuntimeConfig> solRuntimeCaptor = ArgumentCaptor.forClass(ChainRuntimeConfig.class);

		verify(evmScanner, times(2)).scan(evmRuntimeCaptor.capture(), evmWatchersCaptor.capture());
		verify(solScanner).scan(solRuntimeCaptor.capture(), solWatchersCaptor.capture());

		ChainRuntimeConfig ethRuntime = evmRuntimeCaptor.getAllValues().get(0);
		RuntimeWatchers ethWatchers = evmWatchersCaptor.getAllValues().get(0);
		assertEquals("ETH", ethRuntime.chain());
		assertEquals("mainnet", ethRuntime.network());
		assertEquals("https://eth-rpc", ethRuntime.rpcUrl());
		assertTrue(ethWatchers.watchAddressSet().contains("0xabcdefabcdefabcdefabcdefabcdefabcdef1234"));
		assertTrue(ethWatchers.watchAddressTopics().contains(
			"0x000000000000000000000000abcdefabcdefabcdefabcdefabcdefabcdef1234"));
		assertEquals(1, ethWatchers.watchAddressSet().size());
		assertEquals(1, ethWatchers.watchAddressTopics().size());

		ChainRuntimeConfig solRuntime = solRuntimeCaptor.getValue();
		RuntimeWatchers solWatchers = solWatchersCaptor.getValue();
		assertEquals("SOL", solRuntime.chain());
		assertEquals("mainnet", solRuntime.network());
		assertEquals("https://sol-rpc", solRuntime.rpcUrl());
		assertTrue(solWatchers.watchAddressSet().contains(solAddress));
		assertTrue(solWatchers.watchAddressTopics().isEmpty());
		assertEquals(1, solWatchers.watchAddressSet().size());

		ChainRuntimeConfig bscRuntime = evmRuntimeCaptor.getAllValues().get(1);
		RuntimeWatchers bscWatchers = evmWatchersCaptor.getAllValues().get(1);
		assertEquals("BSC", bscRuntime.chain());
		assertEquals("mainnet", bscRuntime.network());
		assertEquals("https://bsc-rpc", bscRuntime.rpcUrl());
		assertTrue(bscWatchers.watchAddressSet().contains(bscAddress));
		assertTrue(bscWatchers.watchAddressTopics().contains(
			"0x0000000000000000000000003333333333333333333333333333333333333333"));
	}

	@Test
	void shouldSkipWhenRpcUrlIsNotHttp() {
		MonitorAddressScopeEntity scope = scope(21L, 201L, "ETH", "mainnet", true);
		when(monitorAddressScopeRepository.findByEnabledTrue()).thenReturn(List.of(scope));
		when(monitorAddressRepository.findByIdInAndEnabledTrue(any())).thenReturn(
			List.of(address(201L, "0x1111111111111111111111111111111111111111", true)));

		ChainConfigEntity cfg = chain("ETH", "mainnet", "enc:http");
		when(chainConfigRepository.findByEnabledTrue()).thenReturn(List.of(cfg));
		when(chainConfigRpcUrlCodec.decryptIfNeeded("enc:http", "ETH", "mainnet")).thenReturn("wss://eth-ws-only");
		when(chainConfigRpcUrlCodec.decryptIfNeeded((String) null, "ETH", "mainnet")).thenReturn(null);

		ScannerOrchestratorService service = new ScannerOrchestratorService(
			chainConfigRepository,
			monitorAddressScopeRepository,
			monitorAddressRepository,
			chainConfigRpcUrlCodec,
			List.of(evmScanner, solScanner)
		);

		int inserted = service.runOnce();

		assertEquals(0, inserted);
		verify(evmScanner, never()).scan(any(), any());
		verify(solScanner, never()).scan(any(), any());
	}

	private static MonitorAddressScopeEntity scope(Long id, Long addressId, String chain, String network, boolean enabled) {
		MonitorAddressScopeEntity entity = new MonitorAddressScopeEntity();
		ReflectionTestUtils.setField(entity, "id", id);
		entity.setMonitorAddressId(addressId);
		entity.setChain(chain);
		entity.setNetwork(network);
		entity.setEnabled(enabled);
		return entity;
	}

	private static MonitorAddressEntity address(Long id, String value, boolean enabled) {
		MonitorAddressEntity entity = new MonitorAddressEntity();
		ReflectionTestUtils.setField(entity, "id", id);
		entity.setAddress(value);
		entity.setEnabled(enabled);
		return entity;
	}

	private static ChainConfigEntity chain(String chain, String network, String rpcHttpUrl) {
		ChainConfigEntity entity = new ChainConfigEntity();
		entity.setChain(chain);
		entity.setNetwork(network);
		entity.setRpcHttpUrl(rpcHttpUrl);
		entity.setRpcUrl(null);
		entity.setConfirmRequired(12);
		entity.setEnabled(true);
		return entity;
	}
}
