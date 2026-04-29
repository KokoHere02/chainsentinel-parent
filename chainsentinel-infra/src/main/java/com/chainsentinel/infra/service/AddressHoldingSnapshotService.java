package com.chainsentinel.infra.service;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
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
import org.web3j.protocol.core.DefaultBlockParameterName;

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
	private static final int WS_BALANCE_RETRY_MAX = 3;
	private static final long WS_HEARTBEAT_INTERVAL_MS = 20_000L;

	private final MonitorAddressRepository monitorAddressRepository;
	private final ChainConfigRepository chainConfigRepository;
	private final AddressTokenHoldingRepository addressTokenHoldingRepository;
	private final MonitorAddressScopeRepository monitorAddressScopeRepository;
	private final ChainConfigRpcUrlCodec chainConfigRpcUrlCodec;
	private final HoldingSnapshotProperties properties;
	private final MeterRegistry meterRegistry;
	private final Web3jClientFactory web3jClientFactory;
	private final SolanaRpcService solanaRpcService;

	public AddressHoldingSnapshotService(
		MonitorAddressRepository monitorAddressRepository,
		ChainConfigRepository chainConfigRepository,
		AddressTokenHoldingRepository addressTokenHoldingRepository,
		MonitorAddressScopeRepository monitorAddressScopeRepository,
		ChainConfigRpcUrlCodec chainConfigRpcUrlCodec,
		HoldingSnapshotProperties properties,
		MeterRegistry meterRegistry,
		Web3jClientFactory web3jClientFactory,
		SolanaRpcService solanaRpcService
	) {
		this.monitorAddressRepository = monitorAddressRepository;
		this.chainConfigRepository = chainConfigRepository;
		this.addressTokenHoldingRepository = addressTokenHoldingRepository;
		this.monitorAddressScopeRepository = monitorAddressScopeRepository;
		this.chainConfigRpcUrlCodec = chainConfigRpcUrlCodec;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
		this.web3jClientFactory = web3jClientFactory;
		this.solanaRpcService = solanaRpcService;
	}

	@Transactional
	public SnapshotResult refreshNativeHoldings() {
		return refreshNativeHoldings(null, null, null);
	}

	@Transactional
	public SnapshotResult refreshNativeHoldings(String chainFilter, String networkFilter, String addressFilter) {
		List<MonitorAddressScopeEntity> enabledScopes = monitorAddressScopeRepository.findByEnabledTrue();
		if (enabledScopes.isEmpty()) {
			log.info("holding.snapshot.skip reason=no_enabled_address_scopes");
			return new SnapshotResult(0, 0, 0);
		}
		List<MonitorAddressScopeEntity> filteredScopes = filterScopes(enabledScopes, chainFilter, networkFilter);
		if (filteredScopes.isEmpty()) {
			log.info("holding.snapshot.skip reason=no_scope_match_filter chain={} network={}", chainFilter, networkFilter);
			return new SnapshotResult(0, 0, 0);
		}

		Set<Long> addressIds = filteredScopes.stream().map(MonitorAddressScopeEntity::getMonitorAddressId).collect(Collectors.toSet());
		Map<Long, MonitorAddressEntity> addressMap = monitorAddressRepository.findByIdInAndEnabledTrue(List.copyOf(addressIds))
			.stream()
			.collect(Collectors.toMap(MonitorAddressEntity::getId, item -> item));
		if (addressMap.isEmpty()) {
			log.info("holding.snapshot.skip reason=no_enabled_monitor_addresses");
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
			log.info("holding.snapshot.skip reason=no_enabled_chain_config");
			return new SnapshotResult(0, 0, 0);
		}

		Map<String, List<MonitorAddressScopeEntity>> scopesByChainNetwork = filteredScopes.stream()
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
				log.info("holding.snapshot.skip chainNetwork={} reason=no_enabled_chain_config", entry.getKey());
				continue;
			}
			String chain = normalizeText(chainConfig.getChain());
			String network = normalizeText(chainConfig.getNetwork());
			if (shouldUseSolanaWsBalance(chainConfig, chain, network)) {
				log.info("holding.snapshot.skip chain={} network={} reason=sol_ws_balance_mode_enabled", chain, network);
				continue;
			}
			String rpcUrl = resolveBalanceRpcUrl(chainConfig, chain, network);
			if (!StringUtils.hasText(rpcUrl)) {
				log.warn("holding.snapshot.skip chain={} network={} reason=rpc_url_empty", chain, network);
				continue;
			}
				if (isSolanaChain(chain)) {
					for (MonitorAddressScopeEntity scope : entry.getValue()) {
						MonitorAddressEntity monitorAddress = addressMap.get(scope.getMonitorAddressId());
						if (monitorAddress == null) {
							continue;
						}
						String normalizedAddress = normalizeAddress(chain, monitorAddress.getAddress());
						if (!matchesAddressFilter(normalizedAddress, chain, addressFilter)) {
							continue;
						}
						if (normalizedAddress == null) {
							failed++;
							chainFailed++;
						log.warn("holding.snapshot.skip reason=invalid_address id={} address={}",
							monitorAddress.getId(), monitorAddress.getAddress());
						continue;
					}

					scanned++;
					chainScanned++;
					try {
						BigInteger balance = solanaRpcService.getBalanceLamports(rpcUrl, normalizedAddress);
						int delta = upsertIfChanged(scope.getId(), chainConfig, normalizedAddress, balance);
						changed += delta;
						chainChanged += delta;
					} catch (Exception ex) {
						failed++;
						chainFailed++;
						log.warn("holding.snapshot rpc failed chain={} network={} address={}: {}",
							chain, network, normalizedAddress, ex.getMessage());
					}
				}
			} else {
				try (RpcSession session = openSession(rpcUrl)) {
						for (MonitorAddressScopeEntity scope : entry.getValue()) {
							MonitorAddressEntity monitorAddress = addressMap.get(scope.getMonitorAddressId());
							if (monitorAddress == null) {
								continue;
							}
							String normalizedAddress = normalizeAddress(chain, monitorAddress.getAddress());
							if (!matchesAddressFilter(normalizedAddress, chain, addressFilter)) {
								continue;
							}
							if (normalizedAddress == null) {
								failed++;
								chainFailed++;
							log.warn("holding.snapshot.skip reason=invalid_address id={} address={}",
								monitorAddress.getId(), monitorAddress.getAddress());
							continue;
						}

						scanned++;
						chainScanned++;
						try {
							BigInteger balance = fetchBalanceWithRetry(session, normalizedAddress, chain, network);
							int delta = upsertIfChanged(scope.getId(), chainConfig, normalizedAddress, balance);
							changed += delta;
							chainChanged += delta;
						} catch (Exception ex) {
							failed++;
							chainFailed++;
							log.warn("holding.snapshot rpc failed chain={} network={} address={}: {}",
								chain, network, normalizedAddress, ex.getMessage());
						}
					}
				}
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

	private List<MonitorAddressScopeEntity> filterScopes(
		List<MonitorAddressScopeEntity> scopes,
		String chainFilter,
		String networkFilter
	) {
		if (!StringUtils.hasText(chainFilter) && !StringUtils.hasText(networkFilter)) {
			return scopes;
		}
		List<MonitorAddressScopeEntity> result = new ArrayList<>();
		for (MonitorAddressScopeEntity scope : scopes) {
			if (StringUtils.hasText(chainFilter) && !equalsIgnoreCase(scope.getChain(), chainFilter)) {
				continue;
			}
			if (StringUtils.hasText(networkFilter) && !equalsIgnoreCase(scope.getNetwork(), networkFilter)) {
				continue;
			}
			result.add(scope);
		}
		return result;
	}

	private boolean matchesAddressFilter(String normalizedAddress, String chain, String addressFilter) {
		if (!StringUtils.hasText(addressFilter)) {
			return true;
		}
		if (!StringUtils.hasText(normalizedAddress)) {
			return false;
		}
		String normalizedFilter = normalizeAddress(chain, addressFilter);
		return StringUtils.hasText(normalizedFilter) && normalizedAddress.equals(normalizedFilter);
	}

	private boolean equalsIgnoreCase(String left, String right) {
		if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
			return false;
		}
		return left.trim().equalsIgnoreCase(right.trim());
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

	private String normalizeAddress(String chain, String address) {
		if (!StringUtils.hasText(address)) {
			return null;
		}
		if (isSolanaChain(chain)) {
			String trimmed = address.trim();
			if (trimmed.length() < 32 || trimmed.length() > 44) {
				return null;
			}
			if (!isValidSolanaBase58(trimmed)) {
				return null;
			}
			return trimmed;
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

	private boolean isValidSolanaBase58(String value) {
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			boolean digit = c >= '1' && c <= '9';
			boolean upper = c >= 'A' && c <= 'Z' && c != 'I' && c != 'O';
			boolean lower = c >= 'a' && c <= 'z' && c != 'l';
			if (!digit && !upper && !lower) {
				return false;
			}
		}
		return true;
	}

	private String normalizeText(String value) {
		return value == null ? null : value.trim();
	}

	private String key(String chain, String network) {
		return normalizeText(chain) + "|" + normalizeText(network);
	}

	private String resolveBalanceRpcUrl(ChainConfigEntity chainConfig, String chain, String network) {
		if (isSolanaChain(chain)) {
			String httpRpc = decryptUrl(chainConfig.getRpcHttpUrl(), chain, network);
			if (StringUtils.hasText(httpRpc)) {
				return httpRpc;
			}
			return decryptUrl(chainConfig.getRpcUrl(), chain, network);
		}

		String protocol = normalizeText(chainConfig.getActiveProtocol());
		if ("WS".equalsIgnoreCase(protocol)) {
			String wsRpc = decryptUrl(chainConfig.getRpcWsUrl(), chain, network);
			if (StringUtils.hasText(wsRpc)) {
				return wsRpc;
			}
			log.warn("holding.snapshot.ws.fallback_http chain={} network={} reason=ws_rpc_empty", chain, network);
		}

		String httpRpc = decryptUrl(chainConfig.getRpcHttpUrl(), chain, network);
		if (StringUtils.hasText(httpRpc)) {
			return httpRpc;
		}
		return decryptUrl(chainConfig.getRpcUrl(), chain, network);
	}

	private boolean shouldUseSolanaWsBalance(ChainConfigEntity chainConfig, String chain, String network) {
		if (!properties.isSolWsEnabled() || !isSolanaChain(chain)) {
			return false;
		}
		String protocol = normalizeText(chainConfig.getActiveProtocol());
		if (!"WS".equalsIgnoreCase(protocol)) {
			return false;
		}
		String wsRpc = decryptUrl(chainConfig.getRpcWsUrl(), chain, network);
		if (isWsRpcUrl(wsRpc)) {
			return true;
		}
		String legacy = decryptUrl(chainConfig.getRpcUrl(), chain, network);
		return isWsRpcUrl(legacy);
	}

	private boolean isSolanaChain(String chain) {
		if (!StringUtils.hasText(chain)) {
			return false;
		}
		String normalized = chain.trim().toUpperCase(Locale.ROOT);
		return "SOL".equals(normalized) || "SOLANA".equals(normalized);
	}

	private String decryptUrl(String encryptedOrRaw, String chain, String network) {
		if (!StringUtils.hasText(encryptedOrRaw)) {
			return null;
		}
		return chainConfigRpcUrlCodec.decryptIfNeeded(encryptedOrRaw, chain, network);
	}

	private RpcSession openSession(String rpcUrl) {
		return new RpcSession(rpcUrl, isWsRpcUrl(rpcUrl), web3jClientFactory.open(rpcUrl));
	}

	private BigInteger fetchBalanceWithRetry(RpcSession session, String address, String chain, String network)
		throws IOException {
		int maxAttempts = session.wsMode ? WS_BALANCE_RETRY_MAX : 1;
		IOException lastIo = null;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				if (session.wsMode) {
					ensureWsHeartbeat(session, chain, network);
				}
				return session.client.web3j()
					.ethGetBalance(address, DefaultBlockParameterName.LATEST)
					.send()
					.getBalance();
			} catch (IOException io) {
				lastIo = io;
			} catch (RuntimeException ex) {
				if (!isReconnectableRuntime(ex)) {
					throw ex;
				}
				lastIo = new IOException(ex.getMessage(), ex);
			}

			if (!session.wsMode || attempt >= maxAttempts) {
				break;
			}

			log.warn(
				"holding.snapshot ws reconnect chain={} network={} address={} attempt={}/{}",
				chain, network, address, attempt, maxAttempts
			);
			session.reconnect(web3jClientFactory);
		}
		throw lastIo == null ? new IOException("balance rpc failed") : lastIo;
	}

	private void ensureWsHeartbeat(RpcSession session, String chain, String network) {
		long now = System.currentTimeMillis();
		if (now - session.lastHeartbeatAtMs < WS_HEARTBEAT_INTERVAL_MS) {
			return;
		}
		try {
			session.client.web3j().ethBlockNumber().send();
			session.lastHeartbeatAtMs = now;
		} catch (Exception ex) {
			log.warn("holding.snapshot.ws.heartbeat.failed chain={} network={} error={}", chain, network, ex.getMessage());
			session.reconnect(web3jClientFactory);
			session.lastHeartbeatAtMs = System.currentTimeMillis();
		}
	}

	private boolean isWsRpcUrl(String rpcUrl) {
		String scheme = UrlSchemeSupport.schemeOf(rpcUrl);
		return "ws".equals(scheme) || "wss".equals(scheme);
	}

	private boolean isReconnectableRuntime(RuntimeException ex) {
		Throwable cur = ex;
		while (cur != null) {
			String msg = cur.getMessage();
			if (msg != null) {
				String lower = msg.toLowerCase(Locale.ROOT);
				if (lower.contains("connection")
					|| lower.contains("disconnect")
					|| lower.contains("closed")
					|| lower.contains("timeout")
					|| lower.contains("reset")) {
					return true;
				}
			}
			cur = cur.getCause();
		}
		return false;
	}

	private static final class RpcSession implements AutoCloseable {

		private final String rpcUrl;
		private final boolean wsMode;
		private Web3jClientFactory.Web3jClient client;
		private long lastHeartbeatAtMs;

		private RpcSession(String rpcUrl, boolean wsMode, Web3jClientFactory.Web3jClient client) {
			this.rpcUrl = rpcUrl;
			this.wsMode = wsMode;
			this.client = client;
			this.lastHeartbeatAtMs = System.currentTimeMillis();
		}

		private void reconnect(Web3jClientFactory factory) {
			close();
			this.client = factory.open(rpcUrl);
		}

		@Override
		public void close() {
			if (client == null) {
				return;
			}
			client.close().run();
			client = null;
		}
	}

	public record SnapshotResult(int scanned, int changed, int failed) {
	}
}
