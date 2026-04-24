package com.chainsentinel.infra.service;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.model.TokenType;
import com.chainsentinel.core.service.ScannerService;
import com.chainsentinel.infra.config.ScannerProperties;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.entity.MonitorAddressEntity;
import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import com.chainsentinel.infra.entity.ScanCheckpointEntity;
import com.chainsentinel.infra.repository.AssetEventRepository;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import com.chainsentinel.infra.repository.MonitorAddressScopeRepository;
import com.chainsentinel.infra.repository.ScanCheckpointRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.protocol.http.HttpService;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EthereumScannerService implements ScannerService {

	private static final Logger log = LoggerFactory.getLogger(EthereumScannerService.class);
	private static final int ETH_TRANSFER_LOG_INDEX = -1;
	private static final Event ERC20_TRANSFER_EVENT = new Event("Transfer",
		List.of(new TypeReference<Address>(true) {
		}, new TypeReference<Address>(true) {
		}, new TypeReference<Uint256>() {
		}));
	private static final String ERC20_TRANSFER_TOPIC = EventEncoder.encode(ERC20_TRANSFER_EVENT);

	private final ScannerProperties scannerProperties;
	private final ChainConfigRepository chainConfigRepository;
	private final ScanCheckpointRepository scanCheckpointRepository;
	private final AssetEventRepository assetEventRepository;
	private final MonitorAddressRepository monitorAddressRepository;
	private final MonitorAddressScopeRepository monitorAddressScopeRepository;
	private final AddressAlertMatcher addressAlertMatcher;
	private final MeterRegistry meterRegistry;
	private final ChainConfigRpcUrlCodec chainConfigRpcUrlCodec;
	private final AtomicLong scannerLagBlocks = new AtomicLong();
	private final AtomicLong eventIngestTotal = new AtomicLong();
	private final AtomicLong eventDuplicateTotal = new AtomicLong();

	public EthereumScannerService(
		ScannerProperties scannerProperties,
		ChainConfigRepository chainConfigRepository,
		ScanCheckpointRepository scanCheckpointRepository,
		AssetEventRepository assetEventRepository,
		MonitorAddressRepository monitorAddressRepository,
		MonitorAddressScopeRepository monitorAddressScopeRepository,
		AddressAlertMatcher addressAlertMatcher,
		MeterRegistry meterRegistry,
		ChainConfigRpcUrlCodec chainConfigRpcUrlCodec
	) {
		this.scannerProperties = scannerProperties;
		this.chainConfigRepository = chainConfigRepository;
		this.scanCheckpointRepository = scanCheckpointRepository;
		this.assetEventRepository = assetEventRepository;
		this.monitorAddressRepository = monitorAddressRepository;
		this.monitorAddressScopeRepository = monitorAddressScopeRepository;
		this.addressAlertMatcher = addressAlertMatcher;
		this.meterRegistry = meterRegistry;
		this.chainConfigRpcUrlCodec = chainConfigRpcUrlCodec;
		meterRegistry.gauge("scanner_lag_blocks", scannerLagBlocks);
		meterRegistry.gauge("event_duplicate_rate", this, s -> {
			long total = s.eventIngestTotal.get();
			if (total <= 0) {
				return 0.0;
			}
			return (double) s.eventDuplicateTotal.get() / total;
		});
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
			String rpcUrl = chainConfigRpcUrlCodec.decryptIfNeeded(cfg.getRpcUrl(), cfg.getChain(),
				cfg.getNetwork());
			if (!StringUtils.hasText(rpcUrl)) {
				log.warn("Skip chain {}-{}: rpcUrl is empty", cfg.getChain(), cfg.getNetwork());
				continue;
			}
			cfg.setRpcUrl(rpcUrl);
			ChainRuntimeConfig runtime = toRuntimeConfig(cfg);
			RuntimeWatchers watchers = resolveWatchersForRuntime(runtime, enabledScopes, enabledAddressMap);
			totalInserted += runOnceForRuntime(runtime, watchers);
		}
		return totalInserted;
	}

	private int runOnceForRuntime(ChainRuntimeConfig runtime, RuntimeWatchers watchers) {
		Web3j web3j = Web3j.build(new HttpService(runtime.rpcUrl()));
		try {
			long latest = rpcCallWithRetry("eth_blockNumber", () ->
				web3j.ethBlockNumber().send().getBlockNumber().longValueExact()
			);
			ScanWindow window = resolveWindow(latest, runtime);
			if (window.fromBlock() > window.toBlock()) {
				scannerLagBlocks.set(0L);
				return 0;
			}

			int inserted = 0;
			Map<Long, EthBlock.Block> blockCache = new HashMap<>();
			inserted += ingestErc20TransferLogs(web3j, latest, window.fromBlock(), window.toBlock(), blockCache,
				runtime,
				watchers.watchAddressTopics());
			inserted += ingestEthTransfers(web3j, latest, window.fromBlock(), window.toBlock(), blockCache, runtime,
				watchers.watchAddressSet());

			saveCheckpoint(window.toBlock(), runtime);
			long lagBlocks = Math.max(0L, latest - window.toBlock());
			scannerLagBlocks.set(lagBlocks);
			log.info("Scan completed: chain={}-{}, window=[{}-{}], inserted={}",
				runtime.chain(), runtime.network(), window.fromBlock(), window.toBlock(), inserted);
			return inserted;
		} catch (Exception e) {
			log.error("Scan failed for chain {}-{}", runtime.chain(), runtime.network(), e);
			return 0;
		} finally {
			web3j.shutdown();
		}
	}

	private ChainRuntimeConfig toRuntimeConfig(ChainConfigEntity cfg) {
		return new ChainRuntimeConfig(
			cfg.getChain(),
			cfg.getNetwork(),
			cfg.getRpcUrl(),
			cfg.getConfirmRequired()
		);
	}

	private ScanWindow resolveWindow(long latestBlock, ChainRuntimeConfig runtime) {
		ScanCheckpointEntity checkpoint = scanCheckpointRepository
			.findByChainAndNetwork(runtime.chain(), runtime.network())
			.orElseGet(() -> initCheckpoint(latestBlock, runtime));

		long from = checkpoint.getLastScannedBlock() + 1;
		long to = Math.min(from + scannerProperties.getWindowSize() - 1L, latestBlock);
		return new ScanWindow(from, to);
	}

	private ScanCheckpointEntity initCheckpoint(long latestBlock, ChainRuntimeConfig runtime) {
		ScanCheckpointEntity checkpoint = new ScanCheckpointEntity();
		checkpoint.setChain(runtime.chain());
		checkpoint.setNetwork(runtime.network());
		long start = scannerProperties.getInitialStartBlock();
		if (start <= 0 || start > latestBlock) {
			checkpoint.setLastScannedBlock(Math.max(0L, latestBlock - 1));
		}
		else {
			checkpoint.setLastScannedBlock(Math.max(0L, start - 1));
		}
		return scanCheckpointRepository.save(checkpoint);
	}

	private int ingestErc20TransferLogs(
		Web3j web3j,
		long latest,
		long from,
		long to,
		Map<Long, EthBlock.Block> blockCache,
		ChainRuntimeConfig runtime,
		List<String> watchAddressTopics
	) throws IOException {
		if (watchAddressTopics.isEmpty()) {
			log.info("No enabled monitor addresses for chain {}, skip ERC20 log scan", runtime.chain());
			return 0;
		}

		int inserted = 0;
		for (String watchAddressTopic : watchAddressTopics) {
			inserted += ingestErc20TransferLogsByAddressRange(web3j, latest, from, to, blockCache, runtime, 1,
				watchAddressTopic);
			inserted += ingestErc20TransferLogsByAddressRange(web3j, latest, from, to, blockCache, runtime, 2,
				watchAddressTopic);
		}
		return inserted;
	}

	private int ingestErc20TransferLogsByAddressRange(
		Web3j web3j,
		long latest,
		long from,
		long to,
		Map<Long, EthBlock.Block> blockCache,
		ChainRuntimeConfig runtime,
		int watchedTopicIndex,
		String watchedAddressTopic
	) throws IOException {
		if (from > to) {
			return 0;
		}

		EthFilter filter = new EthFilter(
			DefaultBlockParameter.valueOf(BigInteger.valueOf(from)),
			DefaultBlockParameter.valueOf(BigInteger.valueOf(to)),
			List.of()
		);
		filter.addSingleTopic(ERC20_TRANSFER_TOPIC);
		if (watchedTopicIndex == 1) {
			filter.addSingleTopic(watchedAddressTopic);
		}
		else if (watchedTopicIndex == 2) {
			filter.addNullTopic();
			filter.addSingleTopic(watchedAddressTopic);
		}
		else {
			throw new IllegalArgumentException("Unsupported watchedTopicIndex: " + watchedTopicIndex);
		}

		EthLog logs = rpcCallWithRetry("eth_getLogs", () -> web3j.ethGetLogs(filter).send());
		if (isTooManyLogsResponse(logs)) {
			if (from == to) {
				log.warn("Skip block {} for ERC20 logs due to provider result-size limit: {}",
					from, logs.getError() == null ? "unknown" : logs.getError().getMessage());
				return 0;
			}
			long mid = from + (to - from) / 2;
			int left = ingestErc20TransferLogsByAddressRange(web3j, latest, from, mid, blockCache, runtime,
				watchedTopicIndex, watchedAddressTopic);
			int right = ingestErc20TransferLogsByAddressRange(web3j, latest, mid + 1, to, blockCache, runtime,
				watchedTopicIndex, watchedAddressTopic);
			return left + right;
		}

		return ingestErc20TransferLogResults(logs.getLogs(), web3j, latest, blockCache, runtime);
	}

	private int ingestErc20TransferLogResults(
		List<EthLog.LogResult> logResults,
		Web3j web3j,
		long latest,
		Map<Long, EthBlock.Block> blockCache,
		ChainRuntimeConfig runtime
	) throws IOException {
		if (logResults == null || logResults.isEmpty()) {
			return 0;
		}

		int inserted = 0;
		for (EthLog.LogResult result : logResults) {
			EthLog.LogObject logObject = (EthLog.LogObject) result.get();
			if (logObject.getTopics().size() != 3) {
				continue;
			}
			if (!isValidUint256Hex(logObject.getData())) {
				continue;
			}

			long blockNumber = logObject.getBlockNumber().longValueExact();
			EthBlock.Block block = getBlock(web3j, blockNumber, blockCache);
			String fromAddress = topicToAddress(logObject.getTopics().get(1));
			String toAddress = topicToAddress(logObject.getTopics().get(2));
			BigInteger amountValue = hexToBigInteger(logObject.getData());
			int confirmations = confirmations(latest, blockNumber);

			AssetEventEntity event = new AssetEventEntity();
			event.setChain(runtime.chain());
			event.setNetwork(runtime.network());
			event.setBlockNumber(blockNumber);
			event.setBlockHash(logObject.getBlockHash());
			event.setTxHash(logObject.getTransactionHash());
			event.setLogIndex(logObject.getLogIndex().intValue());
			event.setFromAddress(fromAddress);
			event.setToAddress(toAddress);
			event.setTokenType(TokenType.ERC20);
			event.setTokenContract(logObject.getAddress());
			event.setSymbol(null);
			event.setAmount(amountValue.toString());
			event.setDecimals(null);
			event.setConfirmations(confirmations);
			event.setStatus(statusByConfirmations(confirmations, runtime));
			event.setOccurredAt(Instant.ofEpochSecond(block.getTimestamp().longValueExact()));
			event.setIngestedAt(Instant.now());

			inserted += upsertEvent(event, true);
		}
		return inserted;
	}

	private int ingestEthTransfers(
		Web3j web3j,
		long latest,
		long from,
		long to,
		Map<Long, EthBlock.Block> blockCache,
		ChainRuntimeConfig runtime,
		Set<String> watchAddressSet
	) throws IOException {
		if (watchAddressSet.isEmpty()) {
			log.info("No enabled monitor addresses for chain {}, skip ETH transfer scan", runtime.chain());
			return 0;
		}

		int inserted = 0;
		for (long blockNumber = from; blockNumber <= to; blockNumber++) {
			EthBlock.Block block = getBlock(web3j, blockNumber, blockCache);
			Instant occurredAt = Instant.ofEpochSecond(block.getTimestamp().longValueExact());
			for (EthBlock.TransactionResult<?> txResult : block.getTransactions()) {
				Transaction tx = (Transaction) txResult.get();
				if (tx.getValue() == null || tx.getValue().signum() <= 0 || tx.getTo() == null) {
					continue;
				}
				if (!isWatchedAddressTransfer(tx, watchAddressSet)) {
					continue;
				}

				int confirmations = confirmations(latest, blockNumber);
				AssetEventEntity event = new AssetEventEntity();
				event.setChain(runtime.chain());
				event.setNetwork(runtime.network());
				event.setBlockNumber(blockNumber);
				event.setBlockHash(block.getHash());
				event.setTxHash(tx.getHash());
				event.setLogIndex(ETH_TRANSFER_LOG_INDEX);
				event.setFromAddress(tx.getFrom());
				event.setToAddress(tx.getTo());
				event.setTokenType(TokenType.ETH);
				event.setTokenContract(null);
				event.setSymbol("ETH");
				BigInteger amountValue = tx.getValue();
				event.setAmount(amountValue.toString());
				event.setDecimals(18);
				event.setConfirmations(confirmations);
				event.setStatus(statusByConfirmations(confirmations, runtime));
				event.setOccurredAt(occurredAt);
				event.setIngestedAt(Instant.now());

				inserted += upsertEvent(event, true);
			}
		}
		return inserted;
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
		for (MonitorAddressScopeEntity scope : enabledScopes) {
			if (!equalsIgnoreCase(scope.getChain(), runtime.chain()) || !equalsIgnoreCase(scope.getNetwork(), runtime.network())) {
				continue;
			}
			MonitorAddressEntity addressEntity = enabledAddressMap.get(scope.getMonitorAddressId());
			if (addressEntity == null) {
				continue;
			}
			String normalizedAddress = normalizeAddress(addressEntity.getAddress());
			if (normalizedAddress == null) {
				continue;
			}
			addressSet.add(normalizedAddress);
			String topic = addressToTopic(normalizedAddress);
			if (StringUtils.hasText(topic)) {
				topicSet.add(topic);
			}
		}
		return new RuntimeWatchers(List.copyOf(topicSet), addressSet);
	}

	private boolean equalsIgnoreCase(String left, String right) {
		if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
			return false;
		}
		return left.trim().equalsIgnoreCase(right.trim());
	}

	private boolean isWatchedAddressTransfer(Transaction tx, Set<String> watchAddressSet) {
		String from = normalizeAddress(tx.getFrom());
		String to = normalizeAddress(tx.getTo());
		return (from != null && watchAddressSet.contains(from))
			|| (to != null && watchAddressSet.contains(to));
	}

	private String addressToTopic(String address) {
		String normalized = normalizeAddress(address);
		if (normalized == null) {
			return null;
		}
		return "0x" + "0".repeat(24) + normalized.substring(2);
	}

	private String normalizeAddress(String address) {
		if (!StringUtils.hasText(address)) {
			return null;
		}
		String normalized = lower(address).trim();
		if (!normalized.startsWith("0x")) {
			normalized = "0x" + normalized;
		}
		if (normalized.length() != 42) {
			return null;
		}
		return normalized;
	}

	private String lower(String v) {
		return v == null ? null : v.toLowerCase();
	}

	private EthBlock.Block getBlock(Web3j web3j, long blockNumber, Map<Long, EthBlock.Block> cache) throws IOException {
		EthBlock.Block block = cache.get(blockNumber);
		if (block != null) {
			return block;
		}
		EthBlock.Block loaded = rpcCallWithRetry("eth_getBlockByNumber:" + blockNumber,
			() -> web3j.ethGetBlockByNumber(
			DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)),
			true
		).send().getBlock());
		cache.put(blockNumber, loaded);
		return loaded;
	}

	private int upsertEvent(AssetEventEntity incoming, boolean evaluateAlert) {
		eventIngestTotal.incrementAndGet();
		meterRegistry.counter("event_ingest_total").increment();
		Optional<AssetEventEntity> existingOpt = assetEventRepository.findByChainAndTxHashAndLogIndex(
			incoming.getChain(), incoming.getTxHash(), incoming.getLogIndex());
		if (existingOpt.isPresent()) {
			eventDuplicateTotal.incrementAndGet();
			meterRegistry.counter("event_duplicate_total").increment();
			AssetEventEntity existing = existingOpt.get();
			existing.setConfirmations(incoming.getConfirmations());
			existing.setStatus(incoming.getStatus());
			AssetEventEntity saved = assetEventRepository.save(existing);
			if (evaluateAlert) {
				addressAlertMatcher.evaluate(saved);
			}
			return 0;
		}
		AssetEventEntity saved = assetEventRepository.save(incoming);
		if (evaluateAlert) {
			addressAlertMatcher.evaluate(saved);
		}
		return 1;
	}

	private void saveCheckpoint(long block, ChainRuntimeConfig runtime) {
		ScanCheckpointEntity checkpoint = scanCheckpointRepository
			.findByChainAndNetwork(runtime.chain(), runtime.network())
			.orElseThrow(() -> new IllegalStateException("Checkpoint must exist"));
		checkpoint.setLastScannedBlock(block);
		scanCheckpointRepository.save(checkpoint);
	}

	private EventStatus statusByConfirmations(int confirmations, ChainRuntimeConfig runtime) {
		return confirmations >= runtime.confirmRequired() ? EventStatus.CONFIRMED : EventStatus.PENDING;
	}

	private int confirmations(long latest, long blockNumber) {
		return (int) (latest - blockNumber + 1);
	}

	private String topicToAddress(String topic) {
		if (topic == null || topic.length() < 40) {
			return null;
		}
		return "0x" + topic.substring(topic.length() - 40);
	}

	private BigInteger hexToBigInteger(String hex) {
		String value = hex == null ? "0x0" : hex;
		return org.web3j.utils.Numeric.decodeQuantity(value);
	}

	private boolean isTooManyLogsResponse(EthLog logs) {
		if (logs == null || logs.getError() == null) {
			return false;
		}
		String msg = logs.getError().getMessage();
		if (!StringUtils.hasText(msg)) {
			return false;
		}
		String lower = msg.toLowerCase();
		return lower.contains("exceeds the limit")
			|| lower.contains("logs count")
			|| lower.contains("too many")
			|| lower.contains("more than")
			|| lower.contains("result size");
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
				log.warn("RPC call failed (operation={}, attempt={}/{}), retry in {} ms: {}",
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

	private boolean isValidUint256Hex(String hex) {
		if (!StringUtils.hasText(hex)) {
			return false;
		}
		if ("0x".equalsIgnoreCase(hex)) {
			return false;
		}
		if (!hex.startsWith("0x") && !hex.startsWith("0X")) {
			return false;
		}
		String body = hex.substring(2);
		if (body.length() != 64) {
			return false;
		}
		for (int i = 0; i < body.length(); i++) {
			char c = body.charAt(i);
			boolean isHex = (c >= '0' && c <= '9')
				|| (c >= 'a' && c <= 'f')
				|| (c >= 'A' && c <= 'F');
			if (!isHex) {
				return false;
			}
		}
		return true;
	}

	@FunctionalInterface
	private interface RpcSupplier<T> {
		T get() throws IOException;

	}

	private record ScanWindow(long fromBlock, long toBlock) {
	}

	private record ChainRuntimeConfig(String chain, String network, String rpcUrl, int confirmRequired) {
	}

	private record RuntimeWatchers(List<String> watchAddressTopics, Set<String> watchAddressSet) {
	}

}









