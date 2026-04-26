package com.chainsentinel.infra.service;

import com.chainsentinel.infra.config.HoldingSnapshotProperties;
import com.chainsentinel.infra.entity.AddressTokenHoldingEntity;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.entity.MonitorAddressEntity;
import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import com.chainsentinel.infra.entity.MonitorScopeTokenEntity;
import com.chainsentinel.infra.repository.AddressTokenHoldingRepository;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import com.chainsentinel.infra.repository.MonitorAddressScopeRepository;
import com.chainsentinel.infra.repository.MonitorScopeTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SolanaBalanceWsSubscriptionService {

	private static final Logger log = LoggerFactory.getLogger(SolanaBalanceWsSubscriptionService.class);
	private static final Duration WS_CONNECT_TIMEOUT = Duration.ofSeconds(10);
	private static final int SPL_FAILURE_CACHE_LIMIT = 5000;

	private final MonitorAddressScopeRepository monitorAddressScopeRepository;
	private final MonitorAddressRepository monitorAddressRepository;
	private final ChainConfigRepository chainConfigRepository;
	private final MonitorScopeTokenRepository monitorScopeTokenRepository;
	private final AddressTokenHoldingRepository addressTokenHoldingRepository;
	private final ChainConfigRpcUrlCodec chainConfigRpcUrlCodec;
	private final HoldingSnapshotProperties properties;
	private final SolanaRpcService solanaRpcService;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	private final Map<TargetKey, ActiveSubscription> activeSubscriptions = new ConcurrentHashMap<>();
	private final Map<String, WsConnection> connectionsByUrl = new ConcurrentHashMap<>();
	private final ConcurrentLinkedDeque<SplRefreshFailureRecord> splRefreshFailures = new ConcurrentLinkedDeque<>();

	public SolanaBalanceWsSubscriptionService(
		MonitorAddressScopeRepository monitorAddressScopeRepository,
		MonitorAddressRepository monitorAddressRepository,
		ChainConfigRepository chainConfigRepository,
		MonitorScopeTokenRepository monitorScopeTokenRepository,
		AddressTokenHoldingRepository addressTokenHoldingRepository,
		ChainConfigRpcUrlCodec chainConfigRpcUrlCodec,
		HoldingSnapshotProperties properties,
		SolanaRpcService solanaRpcService,
		ObjectMapper objectMapper
	) {
		this.monitorAddressScopeRepository = monitorAddressScopeRepository;
		this.monitorAddressRepository = monitorAddressRepository;
		this.chainConfigRepository = chainConfigRepository;
		this.monitorScopeTokenRepository = monitorScopeTokenRepository;
		this.addressTokenHoldingRepository = addressTokenHoldingRepository;
		this.chainConfigRpcUrlCodec = chainConfigRpcUrlCodec;
		this.properties = properties;
		this.solanaRpcService = solanaRpcService;
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(WS_CONNECT_TIMEOUT)
			.build();
	}

	public void refreshSubscriptions() {
		if (!properties.isSolWsEnabled()) {
			clearAllSubscriptions("sol_ws_disabled");
			refreshSplTokenBalancesByHttp();
			return;
		}

		Map<TargetKey, TargetSpec> desired = resolveDesiredTargets();
		Set<TargetKey> desiredKeys = desired.keySet();
		for (Map.Entry<TargetKey, ActiveSubscription> entry : new ArrayList<>(activeSubscriptions.entrySet())) {
			if (!desiredKeys.contains(entry.getKey())) {
				unsubscribe(entry.getValue(), "target_removed");
			}
		}

		for (Map.Entry<TargetKey, TargetSpec> entry : desired.entrySet()) {
			if (activeSubscriptions.containsKey(entry.getKey())) {
				continue;
			}
			subscribe(entry.getKey(), entry.getValue());
		}
		refreshSplTokenBalancesByHttp();
	}

	@PreDestroy
	public void shutdown() {
		clearAllSubscriptions("service_shutdown");
	}

	private void clearAllSubscriptions(String reason) {
		for (ActiveSubscription subscription : new ArrayList<>(activeSubscriptions.values())) {
			unsubscribe(subscription, reason);
		}
		for (WsConnection connection : new ArrayList<>(connectionsByUrl.values())) {
			connection.close();
		}
		connectionsByUrl.clear();
	}

	private Map<TargetKey, TargetSpec> resolveDesiredTargets() {
		List<MonitorAddressScopeEntity> enabledScopes = monitorAddressScopeRepository.findByEnabledTrue();
		if (enabledScopes.isEmpty()) {
			return Map.of();
		}

		Set<Long> addressIds = enabledScopes.stream()
			.map(MonitorAddressScopeEntity::getMonitorAddressId)
			.filter(java.util.Objects::nonNull)
			.collect(Collectors.toSet());
		if (addressIds.isEmpty()) {
			return Map.of();
		}

		Map<Long, MonitorAddressEntity> addressMap = monitorAddressRepository.findByIdInAndEnabledTrue(List.copyOf(addressIds))
			.stream()
			.collect(Collectors.toMap(MonitorAddressEntity::getId, item -> item));
		if (addressMap.isEmpty()) {
			return Map.of();
		}

		Map<String, ChainConfigEntity> chainConfigMap = chainConfigRepository.findByEnabledTrue().stream()
			.filter(cfg -> isSolanaChain(cfg.getChain()))
			.collect(Collectors.toMap(
				cfg -> key(cfg.getChain(), cfg.getNetwork()),
				cfg -> cfg,
				(left, right) -> left,
				HashMap::new
			));
		if (chainConfigMap.isEmpty()) {
			return Map.of();
		}

		Map<Long, ScopeTokenState> tokenStateByScopeId = resolveTokenState(enabledScopes);
		Map<TargetKey, TargetSpec> desired = new HashMap<>();
		for (MonitorAddressScopeEntity scope : enabledScopes) {
			ChainConfigEntity chainConfig = chainConfigMap.get(key(scope.getChain(), scope.getNetwork()));
			if (chainConfig == null) {
				continue;
			}
			String chain = normalizeText(chainConfig.getChain());
			String network = normalizeText(chainConfig.getNetwork());
			String wsUrl = resolveSolanaWsUrl(chainConfig, chain, network);
			if (!StringUtils.hasText(wsUrl)) {
				continue;
			}

			ScopeTokenState tokenState = tokenStateByScopeId.get(scope.getId());
			if (!isScopeTokenEnabled(tokenState)) {
				continue;
			}

			MonitorAddressEntity monitorAddress = addressMap.get(scope.getMonitorAddressId());
			if (monitorAddress == null) {
				continue;
			}
			String address = normalizeSolanaAddress(monitorAddress.getAddress());
			if (address == null) {
				continue;
			}

			String httpUrl = resolveSolanaHttpUrl(chainConfig, chain, network);
			TargetKey targetKey = new TargetKey(scope.getId(), chain, network, address, wsUrl);
			desired.put(targetKey, new TargetSpec(httpUrl));
		}
		return desired;
	}

	private Map<Long, ScopeTokenState> resolveTokenState(List<MonitorAddressScopeEntity> scopes) {
		Set<Long> scopeIds = scopes.stream()
			.map(MonitorAddressScopeEntity::getId)
			.filter(java.util.Objects::nonNull)
			.collect(Collectors.toSet());
		if (scopeIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, ScopeTokenStateBuilder> builders = new HashMap<>();
		for (Long scopeId : scopeIds) {
			builders.put(scopeId, new ScopeTokenStateBuilder());
		}
		List<MonitorScopeTokenEntity> tokens = monitorScopeTokenRepository.findByMonitorScopeIdInOrderByMonitorScopeIdAscIdAsc(
			List.copyOf(scopeIds));
		for (MonitorScopeTokenEntity token : tokens) {
			ScopeTokenStateBuilder builder = builders.get(token.getMonitorScopeId());
			if (builder == null) {
				continue;
			}
			builder.totalCount++;
			if (!Boolean.TRUE.equals(token.getEnabled())) {
				continue;
			}
			String tokenContract = normalizeText(token.getTokenContract());
			if (StringUtils.hasText(tokenContract)
				&& tokenContract.equalsIgnoreCase(properties.getNativeTokenContract())) {
				// Native balance is handled by accountSubscribe; skip SPL refresh for NATIVE marker.
				continue;
			}
			String mint = normalizeSolanaMint(tokenContract);
			if (!StringUtils.hasText(mint)) {
				log.warn("sol.ws.balance.skip.invalid_token_contract scopeId={} tokenContract={}",
					token.getMonitorScopeId(), token.getTokenContract());
				continue;
			}
			builder.enabledTokens.add(new TokenSpec(mint, normalizeText(token.getSymbol()), token.getDecimals()));
		}
		Map<Long, ScopeTokenState> result = new HashMap<>();
		for (Map.Entry<Long, ScopeTokenStateBuilder> entry : builders.entrySet()) {
			ScopeTokenStateBuilder builder = entry.getValue();
			result.put(entry.getKey(), new ScopeTokenState(builder.totalCount, List.copyOf(builder.enabledTokens)));
		}
		return result;
	}

	private boolean isScopeTokenEnabled(ScopeTokenState state) {
		if (state == null) {
			return true;
		}
		if (state.totalCount() == 0) {
			return true;
		}
		return !state.enabledTokens().isEmpty();
	}

	private void subscribe(TargetKey targetKey, TargetSpec targetSpec) {
		try {
			WsConnection connection = connectionsByUrl.computeIfAbsent(targetKey.wsUrl(), this::openConnection);
			long subId = connection.subscribe(targetKey);
			ActiveSubscription active = new ActiveSubscription(targetKey, targetSpec, connection, subId);
			activeSubscriptions.put(targetKey, active);
			connection.bindSubscription(subId, targetKey);
			log.info("sol.ws.balance.subscribed scopeId={} chain={} network={} address={} subId={}",
				targetKey.scopeId(), targetKey.chain(), targetKey.network(), targetKey.address(), subId);
			seedInitialBalance(active);
		} catch (Exception ex) {
			log.warn("sol.ws.balance.subscribe.failed scopeId={} chain={} network={} address={} error={}",
				targetKey.scopeId(), targetKey.chain(), targetKey.network(), targetKey.address(), ex.getMessage());
		}
	}

	private void seedInitialBalance(ActiveSubscription active) {
		try {
			if (!StringUtils.hasText(active.spec().httpRpcUrl())) {
				log.warn("sol.ws.balance.seed.skip scopeId={} chain={} network={} address={} reason=http_rpc_empty",
					active.key().scopeId(),
					active.key().chain(),
					active.key().network(),
					active.key().address());
				return;
			}
			BigInteger lamports = solanaRpcService.getBalanceLamports(active.spec().httpRpcUrl(), active.key().address());
			handleAccountNotification(active.subscriptionId(), lamports);
			log.info("sol.ws.balance.seeded scopeId={} chain={} network={} address={} lamports={}",
				active.key().scopeId(),
				active.key().chain(),
				active.key().network(),
				active.key().address(),
				lamports);
		} catch (Exception ex) {
			log.warn("sol.ws.balance.seed.failed scopeId={} chain={} network={} address={} error={}",
				active.key().scopeId(),
				active.key().chain(),
				active.key().network(),
				active.key().address(),
				ex.getMessage());
		}
	}

	private void refreshSplTokenBalancesByHttp() {
		List<MonitorAddressScopeEntity> enabledScopes = monitorAddressScopeRepository.findByEnabledTrue();
		if (enabledScopes.isEmpty()) {
			return;
		}

		Set<Long> addressIds = enabledScopes.stream()
			.map(MonitorAddressScopeEntity::getMonitorAddressId)
			.filter(java.util.Objects::nonNull)
			.collect(Collectors.toSet());
		if (addressIds.isEmpty()) {
			return;
		}

		Map<Long, MonitorAddressEntity> addressMap = monitorAddressRepository.findByIdInAndEnabledTrue(List.copyOf(addressIds))
			.stream()
			.collect(Collectors.toMap(MonitorAddressEntity::getId, item -> item));
		if (addressMap.isEmpty()) {
			return;
		}

		Map<String, ChainConfigEntity> chainConfigMap = chainConfigRepository.findByEnabledTrue().stream()
			.filter(cfg -> isSolanaChain(cfg.getChain()))
			.collect(Collectors.toMap(
				cfg -> key(cfg.getChain(), cfg.getNetwork()),
				cfg -> cfg,
				(left, right) -> left,
				HashMap::new
			));
		if (chainConfigMap.isEmpty()) {
			return;
		}

		Map<Long, ScopeTokenState> tokenStateByScopeId = resolveTokenState(enabledScopes);
		for (MonitorAddressScopeEntity scope : enabledScopes) {
			ScopeTokenState tokenState = tokenStateByScopeId.get(scope.getId());
			if (tokenState == null || tokenState.enabledTokens().isEmpty()) {
				continue;
			}

			ChainConfigEntity chainConfig = chainConfigMap.get(key(scope.getChain(), scope.getNetwork()));
			if (chainConfig == null) {
				continue;
			}
			String chain = normalizeText(chainConfig.getChain());
			String network = normalizeText(chainConfig.getNetwork());
			String httpUrl = resolveSolanaHttpUrl(chainConfig, chain, network);
			if (!StringUtils.hasText(httpUrl)) {
				log.warn("sol.ws.spl.skip scopeId={} chain={} network={} address={} reason=http_rpc_empty",
					scope.getId(), chain, network, "unknown");
				continue;
			}

			MonitorAddressEntity monitorAddress = addressMap.get(scope.getMonitorAddressId());
			if (monitorAddress == null) {
				continue;
			}
			String ownerAddress = normalizeSolanaAddress(monitorAddress.getAddress());
			if (!StringUtils.hasText(ownerAddress)) {
				continue;
			}

			for (TokenSpec token : tokenState.enabledTokens()) {
				try {
					SolanaRpcService.SplTokenBalance balance = solanaRpcService.getSplTokenBalanceByOwnerAndMint(
						httpUrl,
						ownerAddress,
						token.mint()
					);
					int decimals = balance.decimals() != null ? balance.decimals() : (token.decimals() == null ? 0 : token.decimals());
					upsertTokenHolding(
						scope.getId(),
						chain,
						network,
						ownerAddress,
						token.mint(),
						token.symbol(),
						decimals,
						balance.amount().toString()
					);
				} catch (Exception ex) {
					log.warn("sol.ws.spl.refresh.failed scopeId={} chain={} network={} address={} mint={} error={}",
						scope.getId(), chain, network, ownerAddress, token.mint(), ex.getMessage());
					recordSplRefreshFailure(scope.getId(), chain, network, ownerAddress, token.mint(), ex.getMessage());
				}
			}
		}
	}

	public List<SplRefreshFailureStat> listSplRefreshFailureTop(Instant fromAt, Instant toAt, int top) {
		int safeTop = Math.max(1, Math.min(100, top));
		Map<String, SplRefreshFailureStatBuilder> grouped = new HashMap<>();
		for (SplRefreshFailureRecord record : splRefreshFailures) {
			if (!withinRange(record.occurredAt(), fromAt, toAt)) {
				continue;
			}
			String key = record.scopeId() + "|" + record.chain() + "|" + record.network() + "|" + record.mint();
			SplRefreshFailureStatBuilder builder = grouped.computeIfAbsent(key, ignored -> new SplRefreshFailureStatBuilder(
				record.scopeId(), record.chain(), record.network(), record.address(), record.mint()
			));
			builder.count++;
			if (builder.lastOccurredAt == null || record.occurredAt().isAfter(builder.lastOccurredAt)) {
				builder.lastOccurredAt = record.occurredAt();
				builder.lastError = record.error();
			}
		}
		return grouped.values().stream()
			.sorted((left, right) -> Long.compare(right.count, left.count))
			.limit(safeTop)
			.map(builder -> new SplRefreshFailureStat(
				builder.scopeId,
				builder.chain,
				builder.network,
				builder.address,
				builder.mint,
				builder.count,
				builder.lastError,
				builder.lastOccurredAt
			))
			.toList();
	}

	private void recordSplRefreshFailure(
		Long scopeId,
		String chain,
		String network,
		String address,
		String mint,
		String error
	) {
		splRefreshFailures.addLast(new SplRefreshFailureRecord(
			scopeId,
			chain,
			network,
			address,
			mint,
			error == null ? "unknown" : error,
			Instant.now()
		));
		while (splRefreshFailures.size() > SPL_FAILURE_CACHE_LIMIT) {
			splRefreshFailures.pollFirst();
		}
	}

	private boolean withinRange(Instant value, Instant fromAt, Instant toAt) {
		if (value == null) {
			return false;
		}
		if (fromAt != null && value.isBefore(fromAt)) {
			return false;
		}
		if (toAt != null && value.isAfter(toAt)) {
			return false;
		}
		return true;
	}

	private void unsubscribe(ActiveSubscription active, String reason) {
		activeSubscriptions.remove(active.key());
		try {
			active.connection().unsubscribe(active.subscriptionId());
		} catch (Exception ex) {
			log.warn("sol.ws.balance.unsubscribe.failed scopeId={} subId={} reason={} error={}",
				active.key().scopeId(), active.subscriptionId(), reason, ex.getMessage());
		}
		active.connection().unbindSubscription(active.subscriptionId());
		if (active.connection().isIdle()) {
			connectionsByUrl.remove(active.key().wsUrl(), active.connection());
			active.connection().close();
		}
		log.info("sol.ws.balance.unsubscribed scopeId={} chain={} network={} address={} subId={} reason={}",
			active.key().scopeId(),
			active.key().chain(),
			active.key().network(),
			active.key().address(),
			active.subscriptionId(),
			reason);
	}

	private WsConnection openConnection(String wsUrl) {
		String validated = UrlSchemeSupport.requireSupported(wsUrl, "rpcWsUrl");
		String scheme = UrlSchemeSupport.schemeOf(validated);
		if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
			throw new IllegalArgumentException("solana ws url must use ws/wss");
		}
		WsConnection connection = new WsConnection(validated);
		connection.connect();
		return connection;
	}

	private void handleAccountNotification(long subscriptionId, BigInteger lamports) {
		TargetKey targetKey = null;
		for (WsConnection connection : connectionsByUrl.values()) {
			targetKey = connection.findBySubscriptionId(subscriptionId);
			if (targetKey != null) {
				break;
			}
		}
		if (targetKey == null) {
			return;
		}

		String newBalance = lamports.toString();
		if (!upsertTokenHolding(
			targetKey.scopeId(),
			targetKey.chain(),
			targetKey.network(),
			targetKey.address(),
			properties.getNativeTokenContract(),
			"SOL",
			9,
			newBalance
		)) {
			log.debug("sol.ws.balance.unchanged scopeId={} address={} lamports={}",
				targetKey.scopeId(), targetKey.address(), lamports);
			return;
		}
		log.info("sol.ws.balance.updated scopeId={} chain={} network={} address={} lamports={} subscriptionId={}",
			targetKey.scopeId(),
			targetKey.chain(),
			targetKey.network(),
			targetKey.address(),
			lamports,
			subscriptionId);
	}

	private boolean upsertTokenHolding(
		Long scopeId,
		String chain,
		String network,
		String address,
		String tokenContract,
		String symbol,
		int decimals,
		String newBalance
	) {
		AddressTokenHoldingEntity entity = addressTokenHoldingRepository
			.findByMonitorScopeIdAndTokenContract(scopeId, tokenContract)
			.orElseGet(AddressTokenHoldingEntity::new);
		if (entity.getId() != null && newBalance.equals(entity.getBalanceRaw())) {
			return false;
		}
		entity.setMonitorScopeId(scopeId);
		entity.setChain(chain);
		entity.setNetwork(network);
		entity.setAddress(address);
		entity.setTokenContract(tokenContract);
		entity.setTokenSymbol(symbol);
		entity.setDecimals(decimals);
		entity.setBalanceRaw(newBalance);
		entity.setBalanceUpdatedAt(Instant.now());
		addressTokenHoldingRepository.save(entity);
		return true;
	}

	private String resolveSolanaWsUrl(ChainConfigEntity config, String chain, String network) {
		String protocol = normalizeText(config.getActiveProtocol());
		if (!"WS".equalsIgnoreCase(protocol)) {
			return null;
		}

		String wsUrl = decryptUrl(config.getRpcWsUrl(), chain, network);
		if (isWsUrl(wsUrl)) {
			return wsUrl;
		}
		String legacy = decryptUrl(config.getRpcUrl(), chain, network);
		if (isWsUrl(legacy)) {
			return legacy;
		}
		return null;
	}

	private String resolveSolanaHttpUrl(ChainConfigEntity config, String chain, String network) {
		String httpUrl = decryptUrl(config.getRpcHttpUrl(), chain, network);
		if (isHttpUrl(httpUrl)) {
			return httpUrl;
		}
		String legacy = decryptUrl(config.getRpcUrl(), chain, network);
		if (isHttpUrl(legacy)) {
			return legacy;
		}
		return null;
	}

	private boolean isWsUrl(String value) {
		if (!StringUtils.hasText(value)) {
			return false;
		}
		String scheme = UrlSchemeSupport.schemeOf(value);
		return "ws".equals(scheme) || "wss".equals(scheme);
	}

	private boolean isHttpUrl(String value) {
		if (!StringUtils.hasText(value)) {
			return false;
		}
		String scheme = UrlSchemeSupport.schemeOf(value);
		return "http".equals(scheme) || "https".equals(scheme);
	}

	private boolean isSolanaChain(String chain) {
		if (!StringUtils.hasText(chain)) {
			return false;
		}
		String normalized = chain.trim().toUpperCase(Locale.ROOT);
		return "SOL".equals(normalized) || "SOLANA".equals(normalized);
	}

	private String normalizeSolanaAddress(String address) {
		if (!StringUtils.hasText(address)) {
			return null;
		}
		String normalized = address.trim();
		if (normalized.length() < 32 || normalized.length() > 44) {
			return null;
		}
		for (int i = 0; i < normalized.length(); i++) {
			char c = normalized.charAt(i);
			boolean digit = c >= '1' && c <= '9';
			boolean upper = c >= 'A' && c <= 'Z' && c != 'I' && c != 'O';
			boolean lower = c >= 'a' && c <= 'z' && c != 'l';
			if (!digit && !upper && !lower) {
				return null;
			}
		}
		return normalized;
	}

	private String normalizeSolanaMint(String mint) {
		if (!StringUtils.hasText(mint)) {
			return null;
		}
		String normalized = mint.trim();
		if (normalized.length() < 32 || normalized.length() > 44) {
			return null;
		}
		return normalized;
	}

	private String decryptUrl(String encryptedOrRaw, String chain, String network) {
		if (!StringUtils.hasText(encryptedOrRaw)) {
			return null;
		}
		return chainConfigRpcUrlCodec.decryptIfNeeded(encryptedOrRaw, chain, network);
	}

	private String normalizeText(String value) {
		return value == null ? null : value.trim();
	}

	private String key(String chain, String network) {
		return normalizeText(chain) + "|" + normalizeText(network);
	}

	private record TargetSpec(String httpRpcUrl) {
	}

	private record TargetKey(Long scopeId, String chain, String network, String address, String wsUrl) {
	}

	private record ActiveSubscription(TargetKey key, TargetSpec spec, WsConnection connection, long subscriptionId) {
	}

	private record ScopeTokenState(int totalCount, List<TokenSpec> enabledTokens) {
	}

	private record TokenSpec(String mint, String symbol, Integer decimals) {
	}

	private static final class ScopeTokenStateBuilder {
		private int totalCount;
		private final List<TokenSpec> enabledTokens = new ArrayList<>();
	}

	private record SplRefreshFailureRecord(
		Long scopeId,
		String chain,
		String network,
		String address,
		String mint,
		String error,
		Instant occurredAt
	) {
	}

	public record SplRefreshFailureStat(
		Long scopeId,
		String chain,
		String network,
		String address,
		String mint,
		long count,
		String lastError,
		Instant lastOccurredAt
	) {
	}

	private static final class SplRefreshFailureStatBuilder {
		private final Long scopeId;
		private final String chain;
		private final String network;
		private final String address;
		private final String mint;
		private long count;
		private String lastError;
		private Instant lastOccurredAt;

		private SplRefreshFailureStatBuilder(Long scopeId, String chain, String network, String address, String mint) {
			this.scopeId = scopeId;
			this.chain = chain;
			this.network = network;
			this.address = address;
			this.mint = mint;
		}
	}

	private final class WsConnection implements WebSocket.Listener {

		private final String wsUrl;
		private final AtomicLong requestSeq = new AtomicLong(1);
		private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
		private final Map<Long, TargetKey> subscriptionToKey = new ConcurrentHashMap<>();
		private volatile WebSocket webSocket;
		private final StringBuilder textBuffer = new StringBuilder();

		private WsConnection(String wsUrl) {
			this.wsUrl = wsUrl;
		}

		private void connect() {
			this.webSocket = httpClient.newWebSocketBuilder()
				.connectTimeout(WS_CONNECT_TIMEOUT)
				.buildAsync(URI.create(wsUrl), this)
				.join();
		}

		private synchronized long subscribe(TargetKey targetKey) throws Exception {
			ArrayNode params = objectMapper.createArrayNode();
			params.add(targetKey.address());
			ObjectNode options = params.addObject();
			options.put("commitment", "confirmed");
			JsonNode result = call("accountSubscribe", params);
			if (!result.isIntegralNumber()) {
				throw new IllegalStateException("accountSubscribe result is not number");
			}
			return result.longValue();
		}

		private synchronized void unsubscribe(long subscriptionId) throws Exception {
			JsonNode params = objectMapper.createArrayNode().add(subscriptionId);
			call("accountUnsubscribe", params);
		}

		private void bindSubscription(long subscriptionId, TargetKey targetKey) {
			subscriptionToKey.put(subscriptionId, targetKey);
		}

		private void unbindSubscription(long subscriptionId) {
			subscriptionToKey.remove(subscriptionId);
		}

		private TargetKey findBySubscriptionId(long subscriptionId) {
			return subscriptionToKey.get(subscriptionId);
		}

		private boolean isIdle() {
			return subscriptionToKey.isEmpty();
		}

		private synchronized JsonNode call(String method, JsonNode params) throws Exception {
			long id = requestSeq.getAndIncrement();
			JsonNode payload = objectMapper.createObjectNode()
				.put("jsonrpc", "2.0")
				.put("id", id)
				.put("method", method)
				.set("params", params);

			CompletableFuture<JsonNode> future = new CompletableFuture<>();
			pending.put(id, future);
			webSocket.sendText(payload.toString(), true).join();
			try {
				return future.get(10, TimeUnit.SECONDS);
			} catch (TimeoutException ex) {
				throw new IllegalStateException("sol ws rpc timeout for method " + method, ex);
			} finally {
				pending.remove(id);
			}
		}

		private void close() {
			try {
				if (webSocket != null) {
					webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "closing").join();
				}
			} catch (Exception ignored) {
			}
		}

		@Override
		public void onOpen(WebSocket webSocket) {
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			textBuffer.append(data);
			if (!last) {
				webSocket.request(1);
				return null;
			}

			String message = textBuffer.toString();
			textBuffer.setLength(0);
			try {
				JsonNode root = objectMapper.readTree(message);
				if (root.has("id")) {
					handleRpcResponse(root);
				} else if (root.has("method")) {
					handleNotification(root);
				}
			} catch (Exception ex) {
				log.warn("sol.ws.balance.parse.failed error={}", ex.getMessage());
			}

			webSocket.request(1);
			return null;
		}

		private void handleRpcResponse(JsonNode root) {
			long id = root.path("id").asLong(-1L);
			if (id < 0) {
				return;
			}
			CompletableFuture<JsonNode> future = pending.get(id);
			if (future == null) {
				return;
			}

			JsonNode error = root.path("error");
			if (!error.isMissingNode() && !error.isNull()) {
				int code = error.path("code").asInt();
				String message = error.path("message").asText("unknown");
				future.completeExceptionally(new IllegalStateException("sol ws rpc error code=" + code + " message=" + message));
				return;
			}

			JsonNode result = root.path("result");
			if (result.isMissingNode() || result.isNull()) {
				future.completeExceptionally(new IllegalStateException("sol ws rpc result missing"));
				return;
			}
			future.complete(result);
		}

		private void handleNotification(JsonNode root) {
			String method = root.path("method").asText("");
			if (!"accountNotification".equals(method)) {
				return;
			}
			JsonNode params = root.path("params");
			long subscriptionId = params.path("subscription").asLong(-1L);
			if (subscriptionId < 0) {
				return;
			}
			String lamportsText = params.path("result").path("value").path("lamports").asText(null);
			if (!StringUtils.hasText(lamportsText) || !lamportsText.chars().allMatch(Character::isDigit)) {
				return;
			}
			handleAccountNotification(subscriptionId, new BigInteger(lamportsText));
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			for (CompletableFuture<JsonNode> future : pending.values()) {
				future.completeExceptionally(new IllegalStateException("sol ws closed status=" + statusCode + " reason=" + reason));
			}
			pending.clear();
			Set<Map.Entry<Long, TargetKey>> staleEntries = new HashSet<>(subscriptionToKey.entrySet());
			for (Map.Entry<Long, TargetKey> entry : staleEntries) {
				subscriptionToKey.remove(entry.getKey());
				activeSubscriptions.remove(entry.getValue());
			}
			connectionsByUrl.remove(wsUrl, this);
			return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			log.warn("sol.ws.balance.connection.error wsUrl={} error={}", wsUrl, error.getMessage());
		}
	}
}
