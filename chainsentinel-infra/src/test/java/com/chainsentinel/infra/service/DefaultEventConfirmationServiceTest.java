package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.infra.config.ConfirmationProperties;
import com.chainsentinel.infra.config.ScannerProperties;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.repository.AssetEventRepository;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultEventConfirmationServiceTest {

	@Mock
	private AssetEventRepository assetEventRepository;
	@Mock
	private ChainConfigRepository chainConfigRepository;
	@Mock
	private ChainConfigRpcUrlCodec chainConfigRpcUrlCodec;
	@Mock
	private ReorgAlertCleanupService reorgAlertCleanupService;
	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

	@Test
	void shouldPromotePendingEventToConfirmedWhenConfirmationsReached() throws Exception {
		DefaultEventConfirmationService service = spy(newService(100));
		ChainConfigEntity chainConfig = chainConfig("https://eth-rpc", 12);
		AssetEventEntity event = pendingEvent(1L, 100L, "0xaaa");

		when(chainConfigRepository.findByEnabledTrue()).thenReturn(List.of(chainConfig));
		when(assetEventRepository.countByChainAndNetworkAndStatus("ETH", "mainnet", EventStatus.PENDING)).thenReturn(1L);
		when(assetEventRepository.findByChainAndNetworkAndStatusAndIdGreaterThanOrderByIdAsc(
			eq("ETH"), eq("mainnet"), eq(EventStatus.PENDING), eq(0L), any(Pageable.class)))
			.thenReturn(List.of(event));
		when(chainConfigRpcUrlCodec.decryptIfNeeded("https://eth-rpc", "ETH", "mainnet")).thenReturn("https://eth-rpc");
		doReturn("0xaaa").when(service).fetchCanonicalBlockHash(chainConfig, 100L);
		doReturn(111L).when(service).fetchLatestBlock(chainConfig);
		when(reorgAlertCleanupService.cancelPendingAlertsForReorgedEvents(List.of())).thenReturn(0);

		int updated = service.advancePendingConfirmations();

		assertEquals(1, updated);
		ArgumentCaptor<List<AssetEventEntity>> captor = ArgumentCaptor.forClass(List.class);
		verify(assetEventRepository).saveAll(captor.capture());
		AssetEventEntity saved = captor.getValue().get(0);
		assertEquals(EventStatus.CONFIRMED, saved.getStatus());
		assertEquals(12, saved.getConfirmations());
		verify(reorgAlertCleanupService).cancelPendingAlertsForReorgedEvents(List.of());
	}

	@Test
	void shouldMarkPendingEventAsReorgedWhenCanonicalHashChanged() throws Exception {
		DefaultEventConfirmationService service = spy(newService(100));
		ChainConfigEntity chainConfig = chainConfig("https://eth-rpc", 12);
		AssetEventEntity event = pendingEvent(1L, 100L, "0xold");

		when(chainConfigRepository.findByEnabledTrue()).thenReturn(List.of(chainConfig));
		when(assetEventRepository.countByChainAndNetworkAndStatus("ETH", "mainnet", EventStatus.PENDING)).thenReturn(1L);
		when(assetEventRepository.findByChainAndNetworkAndStatusAndIdGreaterThanOrderByIdAsc(
			eq("ETH"), eq("mainnet"), eq(EventStatus.PENDING), eq(0L), any(Pageable.class)))
			.thenReturn(List.of(event));
		when(chainConfigRpcUrlCodec.decryptIfNeeded("https://eth-rpc", "ETH", "mainnet")).thenReturn("https://eth-rpc");
		doReturn("0xnew").when(service).fetchCanonicalBlockHash(chainConfig, 100L);
		doReturn(105L).when(service).fetchLatestBlock(chainConfig);
		when(reorgAlertCleanupService.cancelPendingAlertsForReorgedEvents(List.of(1L))).thenReturn(1);

		int updated = service.advancePendingConfirmations();

		assertEquals(1, updated);
		ArgumentCaptor<List<AssetEventEntity>> captor = ArgumentCaptor.forClass(List.class);
		verify(assetEventRepository).saveAll(captor.capture());
		AssetEventEntity saved = captor.getValue().get(0);
		assertEquals(EventStatus.REORGED, saved.getStatus());
		assertEquals(0, saved.getConfirmations());
		verify(service, never()).fetchCanonicalBlockHash(chainConfig, 101L);
		verify(reorgAlertCleanupService).cancelPendingAlertsForReorgedEvents(List.of(1L));
		assertEquals(1.0, meterRegistry.get("event_reorg_total").tag("source", "confirmation").counter().count());
	}

	private DefaultEventConfirmationService newService(int batchSize) {
		ScannerProperties scannerProperties = new ScannerProperties();
		ConfirmationProperties confirmationProperties = new ConfirmationProperties();
		confirmationProperties.setBatchSize(batchSize);
		return new DefaultEventConfirmationService(
			assetEventRepository,
			chainConfigRepository,
			scannerProperties,
			confirmationProperties,
			chainConfigRpcUrlCodec,
			reorgAlertCleanupService,
			meterRegistry
		);
	}

	private static ChainConfigEntity chainConfig(String rpcHttpUrl, int confirmRequired) {
		ChainConfigEntity entity = new ChainConfigEntity();
		entity.setChain("ETH");
		entity.setNetwork("mainnet");
		entity.setRpcHttpUrl(rpcHttpUrl);
		entity.setRpcUrl(null);
		entity.setConfirmRequired(confirmRequired);
		entity.setEnabled(true);
		return entity;
	}

	private static AssetEventEntity pendingEvent(Long id, Long blockNumber, String blockHash) {
		AssetEventEntity entity = new AssetEventEntity();
		ReflectionTestUtils.setField(entity, "id", id);
		entity.setChain("ETH");
		entity.setNetwork("mainnet");
		entity.setBlockNumber(blockNumber);
		entity.setBlockHash(blockHash);
		entity.setStatus(EventStatus.PENDING);
		entity.setConfirmations(1);
		return entity;
	}
}
