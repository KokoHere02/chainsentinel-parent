package com.chainsentinel.infra.service;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.chainsentinel.infra.config.HoldingSnapshotProperties;
import com.chainsentinel.infra.entity.AddressTokenHoldingEntity;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.entity.MonitorAddressEntity;
import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import com.chainsentinel.infra.repository.AddressTokenHoldingRepository;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import com.chainsentinel.infra.repository.MonitorAddressScopeRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AddressHoldingSnapshotService {

	private static final Logger log = LoggerFactory.getLogger(AddressHoldingSnapshotService.class);
	private static final String METRIC_HOLDING_SNAPSHOT_SCANNED_TOTAL = "holding_snapshot_scanned_total";
	private static final String METRIC_HOLDING_SNAPSHOT_CHANGED_TOTAL = "holding_snapshot_changed_total";
	private static final String METRIC_HOLDING_SNAPSHOT_FAILED_TOTAL = "holding_snapshot_failed_total";
	private static final String METRIC_HOLDING_SNAPSHOT_DURATION = "holding_snapshot_duration";

	private final MonitorAddressRepository monitorAddressRepository;
	private final ChainConfigRepository chainConfigRepository;
	private final AddressTokenHoldingRepository addressTokenHoldingRepository;
	private final MonitorAddressScopeRepository monitorAddressScopeRepository;
	private final ChainConfigRpcUrlCodec chainConfigRpcUrlCodec;
	private final HoldingSnapshotProperties properties;
	private final MeterRegistry meterRegistry;

	public AddressHoldingSnapshotService(
		MonitorAddressRepository monitorAddressRepository,
		ChainConfigRepository chainConfigRepository,
		AddressTokenHoldingRepository addressTokenHoldingRepository,
		MonitorAddressScopeRepository monitorAddressScopeRepository,
		ChainConfigRpcUrlCodec chainConfigRpcUrlCodec,
		HoldingSnapshotProperties properties,
		MeterRegistry meterRegistry
	) {
		this.monitorAddressRepository = monitorAddressRepository;
		this.chainConfigRepository = chainConfigRepository;
		this.addressTokenHoldingRepository = addressTokenHoldingRepository;
		this.monitorAddressScopeRepository = monitorAddressScopeRepository;
		this.chainConfigRpcUrlCodec = chainConfigRpcUrlCodec;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
	}

	@Transactional
	public SnapshotResult refreshNativeHoldings() {
		List<MonitorAddressScopeEntity> enabledScopes = monitorAddressScopeRepository.findByEnabledTrue();
		if (enabledScopes.isEmpty()) {
			log.info("holding.snapshot skipped: no enabled address scopes");
			return new SnapshotResult(0, 0, 0);
		}

		Set<Long> addressIds = enabledScopes.stream().map(MonitorAddressScopeEntity::getMonitorAddressId).collect(Collectors.toSet());
		Map<Long, MonitorAddressEntity> addressMap = monitorAddressRepository.findByIdInAndEnabledTrue(List.copyOf(addressIds))
			.stream()
			.collect(Collectors.toMap(MonitorAddressEntity::getId, item -> item));
		if (addressMap.isEmpty()) {
			log.info("holding.snapshot skipped: no enabled monitor addresses");
			return new SnapshotResult(0, 0, 0);
		}

		Map<String, ChainConfigEntity> chainConfigMap = chainConfigRepository.findByEnabledTrue().stream()
			.collect(Collectors.toMap(
				item -> key(item.getChain(), item.getNetwork()),
				item -> item,
				(left, right) -> left,
				LinkedHashMap::new
			));
		if (chainConfigMap.isEmpty()) {
			log.info("holding.snapshot skipped: no enabled chain config");
			return new SnapshotResult(0, 0, 0);
		}

		Map<String, List<MonitorAddressScopeEntity>> scopesByChainNetwork = enabledScopes.stream()
			.collect(Collectors.groupingBy(
				item -> key(item.getChain(), item.getNetwork()),
				LinkedHashMap::new,
				Collectors.toList()
			));

		int scanned = 0;
		int changed = 0;
		int failed = 0;
		for (Map.Entry<String, List<MonitorAddressScopeEntity>> entry : scopesByChainNetwork.entrySet()) {
			long chainStartNs = System.nanoTime();
			int chainScanned = 0;
			int chainChanged = 0;
			int chainFailed = 0;
			ChainConfigEntity chainConfig = chainConfigMap.get(entry.getKey());
			if (chainConfig == null) {
				log.info("holding.snapshot skipped chainNetwork={} reason=no_enabled_chain_config", entry.getKey());
				continue;
			}
			String chain = normalizeText(chainConfig.getChain());
			String network = normalizeText(chainConfig.getNetwork());
			String rpcUrl = chainConfigRpcUrlCodec.decryptIfNeeded(
				chainConfig.getRpcUrl(),
				chain,
				network
			);
			if (!StringUtils.hasText(rpcUrl)) {
				log.warn("holding.snapshot skip {}-{}: rpc url empty", chain, network);
				continue;
			}
			Web3j web3j = Web3j.build(new HttpService(rpcUrl));
			try {
				for (MonitorAddressScopeEntity scope : entry.getValue()) {
					MonitorAddressEntity monitorAddress = addressMap.get(scope.getMonitorAddressId());
					if (monitorAddress == null) {
						continue;
					}
					String normalizedAddress = normalizeAddress(monitorAddress.getAddress());
					if (normalizedAddress == null) {
						failed++;
						chainFailed++;
						log.warn("holding.snapshot skip invalid address id={} address={}",
							monitorAddress.getId(), monitorAddress.getAddress());
						continue;
					}

					scanned++;
					chainScanned++;
					try {
						BigInteger balance = web3j.ethGetBalance(normalizedAddress, DefaultBlockParameterName.LATEST)
							.send()
							.getBalance();
						int delta = upsertIfChanged(scope.getId(), chainConfig, normalizedAddress, balance);
						changed += delta;
						chainChanged += delta;
					} catch (IOException ex) {
						failed++;
						chainFailed++;
						log.warn("holding.snapshot rpc failed chain={} network={} address={}: {}",
							chain, network, normalizedAddress, ex.getMessage());
					}
				}
			} finally {
				web3j.shutdown();
			}
			recordMetrics(chain, network, chainScanned, chainChanged, chainFailed, System.nanoTime() - chainStartNs);
			log.info(
				"holding.snapshot chain.summary chain={} network={} scopes={} scanned={} changed={} failed={} durationMs={}",
				chain, network, entry.getValue().size(), chainScanned, chainChanged, chainFailed,
				(System.nanoTime() - chainStartNs) / 1_000_000
			);
		}
		return new SnapshotResult(scanned, changed, failed);
	}

	private void recordMetrics(String chain, String network, int scanned, int changed, int failed, long durationNs) {
		if (scanned > 0) {
			meterRegistry.counter(METRIC_HOLDING_SNAPSHOT_SCANNED_TOTAL, "chain", chain, "network", network)
				.increment(scanned);
		}
		if (changed > 0) {
			meterRegistry.counter(METRIC_HOLDING_SNAPSHOT_CHANGED_TOTAL, "chain", chain, "network", network)
				.increment(changed);
		}
		if (failed > 0) {
			meterRegistry.counter(METRIC_HOLDING_SNAPSHOT_FAILED_TOTAL, "chain", chain, "network", network)
				.increment(failed);
		}
		meterRegistry.timer(METRIC_HOLDING_SNAPSHOT_DURATION, "chain", chain, "network", network)
			.record(durationNs, java.util.concurrent.TimeUnit.NANOSECONDS);
	}

	private int upsertIfChanged(
		Long monitorScopeId,
		ChainConfigEntity chainConfig,
		String normalizedAddress,
		BigInteger balance
	) {
		String network = normalizeText(chainConfig.getNetwork());
		String tokenContract = normalizeText(properties.getNativeTokenContract());
		String newBalanceRaw = balance.toString();

		AddressTokenHoldingEntity entity = addressTokenHoldingRepository
			.findByMonitorScopeIdAndTokenContract(monitorScopeId, tokenContract)
			.orElseGet(AddressTokenHoldingEntity::new);

		if (entity.getId() != null && newBalanceRaw.equals(entity.getBalanceRaw())) {
			return 0;
		}

		entity.setMonitorScopeId(monitorScopeId);
		entity.setChain(normalizeText(chainConfig.getChain()));
		entity.setNetwork(network);
		entity.setAddress(normalizedAddress);
		entity.setTokenContract(tokenContract);
		entity.setTokenSymbol(normalizeText(properties.getNativeTokenSymbol()));
		entity.setDecimals(properties.getNativeTokenDecimals());
		entity.setBalanceRaw(newBalanceRaw);
		entity.setBalanceUpdatedAt(Instant.now());
		addressTokenHoldingRepository.save(entity);
		return 1;
	}

	private String normalizeAddress(String address) {
		if (!StringUtils.hasText(address)) {
			return null;
		}
		String normalized = address.trim().toLowerCase(Locale.ROOT);
		if (!normalized.startsWith("0x")) {
			normalized = "0x" + normalized;
		}
		if (normalized.length() != 42) {
			return null;
		}
		return normalized;
	}

	private String normalizeText(String value) {
		return value == null ? null : value.trim();
	}

	private String key(String chain, String network) {
		return normalizeText(chain) + "|" + normalizeText(network);
	}

	public record SnapshotResult(int scanned, int changed, int failed) {
	}
}
