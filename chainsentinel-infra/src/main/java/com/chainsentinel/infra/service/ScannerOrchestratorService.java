package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.ScannerService;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.entity.MonitorAddressEntity;
import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import com.chainsentinel.infra.repository.MonitorAddressScopeRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ScannerOrchestratorService implements ScannerService {

	private static final Logger log = LoggerFactory.getLogger(ScannerOrchestratorService.class);

	private final ChainConfigRepository chainConfigRepository;
	private final MonitorAddressScopeRepository monitorAddressScopeRepository;
	private final MonitorAddressRepository monitorAddressRepository;
	private final ChainConfigRpcUrlCodec chainConfigRpcUrlCodec;
	private final List<ChainEventScanner> scanners;

	public ScannerOrchestratorService(
		ChainConfigRepository chainConfigRepository,
		MonitorAddressScopeRepository monitorAddressScopeRepository,
		MonitorAddressRepository monitorAddressRepository,
		ChainConfigRpcUrlCodec chainConfigRpcUrlCodec,
		List<ChainEventScanner> scanners
	) {
		this.chainConfigRepository = chainConfigRepository;
		this.monitorAddressScopeRepository = monitorAddressScopeRepository;
		this.monitorAddressRepository = monitorAddressRepository;
		this.chainConfigRpcUrlCodec = chainConfigRpcUrlCodec;
		this.scanners = scanners;
	}

	@Override
	public int runOnce() {
		List<MonitorAddressScopeEntity> enabledScopes = monitorAddressScopeRepository.findByEnabledTrue();
		if (enabledScopes.isEmpty()) {
			log.info("No enabled monitor scopes, skip scanning");
			return 0;
		}

		Map<Long, MonitorAddressEntity> enabledAddressMap = resolveEnabledAddressMap(enabledScopes);
		if (enabledAddressMap.isEmpty()) {
			log.info("No enabled monitor addresses for scopes, skip scanning");
			return 0;
		}

		List<ChainConfigEntity> configs = chainConfigRepository.findByEnabledTrue();
		if (configs.isEmpty()) {
			log.info("No enabled chain_config, skip scanning");
			return 0;
		}

		int totalInserted = 0;
		for (ChainConfigEntity cfg : configs) {
			String rpcUrl = resolveLogHttpRpcUrl(cfg);
			if (!StringUtils.hasText(rpcUrl)) {
				log.warn("Skip chain {}-{}: rpcUrl is empty", cfg.getChain(), cfg.getNetwork());
				continue;
			}
			ChainRuntimeConfig runtime = new ChainRuntimeConfig(cfg.getChain(), cfg.getNetwork(), rpcUrl, cfg.getConfirmRequired());
			RuntimeWatchers watchers = resolveWatchersForRuntime(runtime, enabledScopes, enabledAddressMap);
			int scopeCount = countMatchedScopesForRuntime(runtime, enabledScopes, enabledAddressMap);
			if (!hasWatchers(watchers)) {
				log.info("Skip chain {}-{}: no enabled watcher addresses for this chain/network",
					runtime.chain(), runtime.network());
				continue;
			}

			ChainEventScanner scanner = resolveScanner(runtime.chain());
			if (scanner == null) {
				log.warn("Skip chain {}-{}: no scanner supports chain {}", runtime.chain(), runtime.network(), runtime.chain());
				continue;
			}
			log.info(
				"Dispatch scan: chain={}-{}, scopes={}, addresses={}, topics={}, scanner={}",
				runtime.chain(),
				runtime.network(),
				scopeCount,
				watchers.watchAddressSet().size(),
				watchers.watchAddressTopics().size(),
				scanner.getClass().getSimpleName()
			);
			totalInserted += scanner.scan(runtime, watchers);
		}
		return totalInserted;
	}

	private ChainEventScanner resolveScanner(String chain) {
		for (ChainEventScanner scanner : scanners) {
			if (scanner.supports(chain)) {
				return scanner;
			}
		}
		return null;
	}

	private boolean hasWatchers(RuntimeWatchers watchers) {
		return watchers != null
			&& (!watchers.watchAddressTopics().isEmpty() || !watchers.watchAddressSet().isEmpty());
	}

	private Map<Long, MonitorAddressEntity> resolveEnabledAddressMap(List<MonitorAddressScopeEntity> scopes) {
		Set<Long> addressIds = new java.util.HashSet<>();
		for (MonitorAddressScopeEntity scope : scopes) {
			if (scope.getMonitorAddressId() != null) {
				addressIds.add(scope.getMonitorAddressId());
			}
		}
		if (addressIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, MonitorAddressEntity> map = new HashMap<>();
		for (MonitorAddressEntity address : monitorAddressRepository.findByIdInAndEnabledTrue(List.copyOf(addressIds))) {
			map.put(address.getId(), address);
		}
		return map;
	}

	private RuntimeWatchers resolveWatchersForRuntime(
		ChainRuntimeConfig runtime,
		List<MonitorAddressScopeEntity> enabledScopes,
		Map<Long, MonitorAddressEntity> enabledAddressMap
	) {
		Set<String> addressSet = new java.util.HashSet<>();
		Set<String> topicSet = new java.util.HashSet<>();
		boolean solana = isSolanaChain(runtime.chain());
		for (MonitorAddressScopeEntity scope : enabledScopes) {
			if (!equalsIgnoreCase(scope.getChain(), runtime.chain()) || !equalsIgnoreCase(scope.getNetwork(), runtime.network())) {
				continue;
			}
			MonitorAddressEntity addressEntity = enabledAddressMap.get(scope.getMonitorAddressId());
			if (addressEntity == null) {
				continue;
			}
			String normalizedAddress = normalizeAddress(runtime.chain(), addressEntity.getAddress());
			if (normalizedAddress == null) {
				continue;
			}
			addressSet.add(normalizedAddress);
			if (!solana) {
				String topic = addressToTopic(normalizedAddress);
				if (StringUtils.hasText(topic)) {
					topicSet.add(topic);
				}
			}
		}
		return new RuntimeWatchers(List.copyOf(topicSet), addressSet);
	}

	private int countMatchedScopesForRuntime(
		ChainRuntimeConfig runtime,
		List<MonitorAddressScopeEntity> enabledScopes,
		Map<Long, MonitorAddressEntity> enabledAddressMap
	) {
		int count = 0;
		for (MonitorAddressScopeEntity scope : enabledScopes) {
			if (!equalsIgnoreCase(scope.getChain(), runtime.chain()) || !equalsIgnoreCase(scope.getNetwork(), runtime.network())) {
				continue;
			}
			MonitorAddressEntity addressEntity = enabledAddressMap.get(scope.getMonitorAddressId());
			if (addressEntity == null) {
				continue;
			}
			String normalizedAddress = normalizeAddress(runtime.chain(), addressEntity.getAddress());
			if (normalizedAddress == null) {
				continue;
			}
			count++;
		}
		return count;
	}

	private String resolveLogHttpRpcUrl(ChainConfigEntity cfg) {
		String chain = cfg.getChain();
		String network = cfg.getNetwork();
		String httpRpc = chainConfigRpcUrlCodec.decryptIfNeeded(cfg.getRpcHttpUrl(), chain, network);
		if (isHttpRpcUrl(httpRpc)) {
			return httpRpc;
		}
		String legacyRpc = chainConfigRpcUrlCodec.decryptIfNeeded(cfg.getRpcUrl(), chain, network);
		if (isHttpRpcUrl(legacyRpc)) {
			return legacyRpc;
		}
		if (StringUtils.hasText(httpRpc) || StringUtils.hasText(legacyRpc)) {
			log.warn("Skip chain {}-{}: log scanner requires http/https rpc url", chain, network);
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

	private boolean isSolanaChain(String chain) {
		if (!StringUtils.hasText(chain)) {
			return false;
		}
		String normalized = chain.trim().toUpperCase();
		return "SOL".equals(normalized) || "SOLANA".equals(normalized);
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
			return trimmed;
		}
		String normalized = address.toLowerCase().trim();
		if (!normalized.startsWith("0x")) {
			normalized = "0x" + normalized;
		}
		return normalized.length() == 42 ? normalized : null;
	}

	private String addressToTopic(String evmAddress) {
		return "0x" + "0".repeat(24) + evmAddress.substring(2);
	}

	private boolean equalsIgnoreCase(String left, String right) {
		if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
			return false;
		}
		return left.trim().equalsIgnoreCase(right.trim());
	}
}
