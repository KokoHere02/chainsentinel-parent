package com.chainsentinel.infra.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.service.EventConfirmationService;
import com.chainsentinel.infra.config.ConfirmationProperties;
import com.chainsentinel.infra.config.ScannerProperties;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.repository.AssetEventRepository;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.protocol.http.HttpService;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultEventConfirmationService implements EventConfirmationService {

	private static final Logger log = LoggerFactory.getLogger(DefaultEventConfirmationService.class);
	private static final String METRIC_EVENT_REORG_TOTAL = "event_reorg_total";

	private final AssetEventRepository assetEventRepository;
	private final ChainConfigRepository chainConfigRepository;
	private final ScannerProperties scannerProperties;
	private final ConfirmationProperties confirmationProperties;
	private final ChainConfigRpcUrlCodec chainConfigRpcUrlCodec;
	private final ReorgAlertCleanupService reorgAlertCleanupService;
	private final MeterRegistry meterRegistry;

	public DefaultEventConfirmationService(
		AssetEventRepository assetEventRepository,
		ChainConfigRepository chainConfigRepository,
		ScannerProperties scannerProperties,
		ConfirmationProperties confirmationProperties,
		ChainConfigRpcUrlCodec chainConfigRpcUrlCodec,
		ReorgAlertCleanupService reorgAlertCleanupService,
		MeterRegistry meterRegistry
	) {
		this.assetEventRepository = assetEventRepository;
		this.chainConfigRepository = chainConfigRepository;
		this.scannerProperties = scannerProperties;
		this.confirmationProperties = confirmationProperties;
		this.chainConfigRpcUrlCodec = chainConfigRpcUrlCodec;
		this.reorgAlertCleanupService = reorgAlertCleanupService;
		this.meterRegistry = meterRegistry;
	}

	@Override
	public int advancePendingConfirmations() {
		int totalUpdated = 0;
		int batchSize = Math.max(1, confirmationProperties.getBatchSize());

		for (ChainConfigEntity chainConfig : chainConfigRepository.findByEnabledTrue()) {
			totalUpdated += advanceForChain(chainConfig, batchSize);
		}
		return totalUpdated;
	}

	private int advanceForChain(ChainConfigEntity chainConfig, int batchSize) {
		String chain = chainConfig.getChain();
		String network = chainConfig.getNetwork();

		long pendingCount = assetEventRepository.countByChainAndNetworkAndStatus(chain, network, EventStatus.PENDING);
		if (pendingCount <= 0) {
			return 0;
		}

		String rpcUrl = resolveLogHttpRpcUrl(chainConfig, chain, network);
		if (!StringUtils.hasText(rpcUrl)) {
			log.warn("Skip confirmation advance for {}-{} because rpcUrl is empty", chain, network);
			return 0;
		}
		chainConfig.setRpcUrl(rpcUrl);

		try {
			long latestBlock = fetchLatestBlock(chainConfig);
			long cursorId = 0L;
			int updated = 0;
			int promoted = 0;
			int reorged = 0;
			Map<Long, String> blockHashCache = new HashMap<>();

			while (true) {
				List<AssetEventEntity> batch = assetEventRepository
					.findByChainAndNetworkAndStatusAndIdGreaterThanOrderByIdAsc(
						chain,
						network,
						EventStatus.PENDING,
						cursorId,
						PageRequest.of(0, batchSize)
					);
				if (batch.isEmpty()) {
					break;
				}

				List<AssetEventEntity> changed = new ArrayList<>();
				for (AssetEventEntity event : batch) {
					cursorId = event.getId();

					if (isReorgedEvent(chainConfig, event, blockHashCache)) {
						event.setConfirmations(0);
						event.setStatus(EventStatus.REORGED);
						changed.add(event);
						reorged++;
						meterRegistry.counter(METRIC_EVENT_REORG_TOTAL, "source", "confirmation").increment();
						continue;
					}

					int confirmations = confirmations(latestBlock, event.getBlockNumber());
					EventStatus status = confirmations >= chainConfig.getConfirmRequired()
						? EventStatus.CONFIRMED
						: EventStatus.PENDING;

					boolean isChanged = !Objects.equals(event.getConfirmations(), confirmations)
						|| event.getStatus() != status;
					if (!isChanged) {
						continue;
					}

					if (status == EventStatus.CONFIRMED) {
						promoted++;
					}
					event.setConfirmations(confirmations);
					event.setStatus(status);
					changed.add(event);
				}

				if (!changed.isEmpty()) {
					assetEventRepository.saveAll(changed);
					updated += changed.size();
					int canceledAlerts = reorgAlertCleanupService.cancelPendingAlertsForReorgedEvents(
						changed.stream()
							.filter(event -> event.getStatus() == EventStatus.REORGED)
							.map(AssetEventEntity::getId)
							.filter(Objects::nonNull)
							.toList()
					);
					if (canceledAlerts > 0) {
						log.warn("Confirmation advance canceled stale alerts: chain={}-{}, canceledAlerts={}",
							chain, network, canceledAlerts);
					}
				}

				if (batch.size() < batchSize) {
					break;
				}
			}

			log.info("Confirmation advance finished: chain={}-{}, pending={}, updated={}, promoted={}, reorged={}, latestBlock={}",
				chain, network, pendingCount, updated, promoted, reorged, latestBlock);
			return updated;
		} catch (Exception ex) {
			log.error("Confirmation advance failed for chain {}-{}", chain, network, ex);
			return 0;
		}
	}

	long fetchLatestBlock(ChainConfigEntity chainConfig) throws IOException {
		return rpcCallWithRetry("eth_blockNumber", () -> {
			Web3j web3j = Web3j.build(new HttpService(chainConfig.getRpcUrl()));
			try {
				BigInteger latest = web3j.ethBlockNumber().send().getBlockNumber();
				return latest.longValueExact();
			} finally {
				web3j.shutdown();
			}
		});
	}

	String fetchCanonicalBlockHash(ChainConfigEntity chainConfig, long blockNumber) throws IOException {
		return rpcCallWithRetry("eth_getBlockByNumber:" + blockNumber, () -> {
			Web3j web3j = Web3j.build(new HttpService(chainConfig.getRpcUrl()));
			try {
				var response = web3j.ethGetBlockByNumber(
					DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)),
					false
				).send();
				return response.getBlock() == null ? null : response.getBlock().getHash();
			} finally {
				web3j.shutdown();
			}
		});
	}

	private String resolveLogHttpRpcUrl(ChainConfigEntity chainConfig, String chain, String network) {
		String httpRpc = chainConfigRpcUrlCodec.decryptIfNeeded(chainConfig.getRpcHttpUrl(), chain, network);
		if (isHttpRpcUrl(httpRpc)) {
			return httpRpc;
		}
		String legacyRpc = chainConfigRpcUrlCodec.decryptIfNeeded(chainConfig.getRpcUrl(), chain, network);
		if (isHttpRpcUrl(legacyRpc)) {
			return legacyRpc;
		}
		if (StringUtils.hasText(httpRpc) || StringUtils.hasText(legacyRpc)) {
			log.warn("Skip confirmation advance for {}-{} because rpc url is not http/https", chain, network);
		}
		return null;
	}

	private boolean isHttpRpcUrl(String rpcUrl) {
		if (!StringUtils.hasText(rpcUrl)) {
			return false;
		}
		String scheme = UrlSchemeSupport.schemeOf(rpcUrl);
		return "http".equals(scheme) || "https".equals(scheme);
	}

	private boolean isReorgedEvent(
		ChainConfigEntity chainConfig,
		AssetEventEntity event,
		Map<Long, String> blockHashCache
	) throws IOException {
		if (event.getBlockNumber() == null || !StringUtils.hasText(event.getBlockHash())) {
			return false;
		}
		String canonicalHash = blockHashCache.get(event.getBlockNumber());
		if (canonicalHash == null && !blockHashCache.containsKey(event.getBlockNumber())) {
			canonicalHash = fetchCanonicalBlockHash(chainConfig, event.getBlockNumber());
			blockHashCache.put(event.getBlockNumber(), canonicalHash);
		}
		return StringUtils.hasText(canonicalHash) && !event.getBlockHash().equalsIgnoreCase(canonicalHash);
	}

	private int confirmations(long latest, long blockNumber) {
		long value = latest - blockNumber + 1;
		if (value <= 0) {
			return 0;
		}
		return (int) value;
	}

	private <T> T rpcCallWithRetry(String operation, RpcSupplier<T> supplier) throws IOException {
		int maxRetries = Math.max(0, scannerProperties.getRpcRetryMax());
		long baseBackoffMs = Math.max(0L, scannerProperties.getRpcRetryBackoffMs());

		int attempt = 0;
		while (true) {
			try {
				return supplier.get();
			} catch (IOException | RuntimeException ex) {
				boolean retryable = isRetryableRpcException(ex);
				if (!retryable || attempt >= maxRetries) {
					if (ex instanceof IOException io) {
						throw io;
					}
					throw ex;
				}
				long sleepMs = baseBackoffMs * (1L << attempt);
				log.warn("RPC call failed in confirmation advance (operation={}, attempt={}/{}), retry in {} ms: {}",
					operation, attempt + 1, maxRetries + 1, sleepMs, ex.getMessage());
				sleepQuietly(sleepMs);
				attempt++;
			}
		}
	}

	private boolean isRetryableRpcException(Throwable ex) {
		Throwable cur = ex;
		while (cur != null) {
			if (cur instanceof IOException || cur instanceof ClientConnectionException) {
				return true;
			}
			String msg = cur.getMessage();
			if (msg != null) {
				String lower = msg.toLowerCase();
				if (lower.contains("503")
					|| lower.contains("429")
					|| lower.contains("timeout")
					|| lower.contains("connection reset")
					|| lower.contains("connection termination")) {
					return true;
				}
			}
			cur = cur.getCause();
		}
		return false;
	}

	private void sleepQuietly(long sleepMs) {
		if (sleepMs <= 0) {
			return;
		}
		try {
			Thread.sleep(sleepMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("RPC retry interrupted", e);
		}
	}

	@FunctionalInterface
	private interface RpcSupplier<T> {
		T get() throws IOException;

	}

}
