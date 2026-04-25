package com.chainsentinel.infra.service;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.model.TokenType;
import com.chainsentinel.infra.config.ScannerProperties;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.entity.ScanCheckpointEntity;
import com.chainsentinel.infra.repository.AssetEventRepository;
import com.chainsentinel.infra.repository.ScanCheckpointRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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

@Component
public class EthereumScannerService implements ChainEventScanner {

	private static final Logger log = LoggerFactory.getLogger(EthereumScannerService.class);
	private static final int ETH_TRANSFER_LOG_INDEX = -1;
	private static final Event ERC20_TRANSFER_EVENT = new Event("Transfer",
		List.of(new TypeReference<Address>(true) {
		}, new TypeReference<Address>(true) {
		}, new TypeReference<Uint256>() {
		}));
	private static final String ERC20_TRANSFER_TOPIC = EventEncoder.encode(ERC20_TRANSFER_EVENT);

	private final ScannerProperties scannerProperties;
	private final ScanCheckpointRepository scanCheckpointRepository;
	private final AssetEventRepository assetEventRepository;
	private final AddressAlertMatcher addressAlertMatcher;
	private final MeterRegistry meterRegistry;
	private final AtomicLong scannerLagBlocks = new AtomicLong();
	private final AtomicLong eventIngestTotal = new AtomicLong();
	private final AtomicLong eventDuplicateTotal = new AtomicLong();

	public EthereumScannerService(
		ScannerProperties scannerProperties,
		ScanCheckpointRepository scanCheckpointRepository,
		AssetEventRepository assetEventRepository,
		AddressAlertMatcher addressAlertMatcher,
		MeterRegistry meterRegistry
	) {
		this.scannerProperties = scannerProperties;
		this.scanCheckpointRepository = scanCheckpointRepository;
		this.assetEventRepository = assetEventRepository;
		this.addressAlertMatcher = addressAlertMatcher;
		this.meterRegistry = meterRegistry;
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
	public boolean supports(String chain) {
		return !isSolanaChain(chain);
	}

	@Override
	public int scan(ChainRuntimeConfig runtime, RuntimeWatchers watchers) {
		if (!hasWatchers(watchers)) {
			log.info("Skip runtime scan: chain={}-{} has no watcher addresses", runtime.chain(), runtime.network());
			return 0;
		}
		return runOnceForRuntime(runtime, watchers);
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
			int reorged = reconcileReorgedEvents(web3j, runtime, window.fromBlock(), window.toBlock(), blockCache);
			inserted += ingestErc20TransferLogs(web3j, latest, window.fromBlock(), window.toBlock(), blockCache,
				runtime,
				watchers.watchAddressTopics());
			inserted += ingestEthTransfers(web3j, latest, window.fromBlock(), window.toBlock(), blockCache, runtime,
				watchers.watchAddressSet());

			saveCheckpoint(window.toBlock(), runtime);
			long lagBlocks = Math.max(0L, latest - window.toBlock());
			scannerLagBlocks.set(lagBlocks);
			log.info("Scan completed: chain={}-{}, window=[{}-{}], inserted={}, reorged={}",
				runtime.chain(), runtime.network(), window.fromBlock(), window.toBlock(), inserted, reorged);
			return inserted;
		} catch (Exception e) {
			log.error("Scan failed for chain {}-{}", runtime.chain(), runtime.network(), e);
			return 0;
		} finally {
			web3j.shutdown();
		}
	}

	private ScanWindow resolveWindow(long latestBlock, ChainRuntimeConfig runtime) {
		ScanCheckpointEntity checkpoint = scanCheckpointRepository
			.findByChainAndNetwork(runtime.chain(), runtime.network())
			.orElseGet(() -> initCheckpoint(latestBlock, runtime));

		long lookback = Math.max(0L, scannerProperties.getReorgLookbackBlocks());
		long from = Math.max(0L, checkpoint.getLastScannedBlock() + 1 - lookback);
		long to = Math.min(from + scannerProperties.getWindowSize() - 1L, latestBlock);
		return new ScanWindow(from, to);
	}

	private int reconcileReorgedEvents(
		Web3j web3j,
		ChainRuntimeConfig runtime,
		long fromBlock,
		long toBlock,
		Map<Long, EthBlock.Block> blockCache
	) throws IOException {
		List<AssetEventEntity> history = assetEventRepository.findByChainAndNetworkAndBlockNumberBetweenOrderByBlockNumberAsc(
			runtime.chain(),
			runtime.network(),
			fromBlock,
			toBlock
		);
		if (history.isEmpty()) {
			return 0;
		}

		Map<Long, List<AssetEventEntity>> byBlock = new LinkedHashMap<>();
		for (AssetEventEntity event : history) {
			if (event.getBlockNumber() == null || event.getStatus() == EventStatus.REORGED) {
				continue;
			}
			byBlock.computeIfAbsent(event.getBlockNumber(), ignored -> new ArrayList<>()).add(event);
		}
		if (byBlock.isEmpty()) {
			return 0;
		}

		List<AssetEventEntity> changed = new ArrayList<>();
		for (Map.Entry<Long, List<AssetEventEntity>> entry : byBlock.entrySet()) {
			long blockNumber = entry.getKey();
			EthBlock.Block block = getBlock(web3j, blockNumber, blockCache);
			if (block == null || !StringUtils.hasText(block.getHash())) {
				continue;
			}
			String canonicalHash = block.getHash();
			for (AssetEventEntity event : entry.getValue()) {
				if (!StringUtils.hasText(event.getBlockHash())) {
					continue;
				}
				if (event.getBlockHash().equalsIgnoreCase(canonicalHash)) {
					continue;
				}
				event.setStatus(EventStatus.REORGED);
				event.setConfirmations(0);
				changed.add(event);
			}
		}

		if (!changed.isEmpty()) {
			assetEventRepository.saveAll(changed);
			log.warn("Reorg reconciled: chain={}-{}, fromBlock={}, toBlock={}, reorgedEvents={}",
				runtime.chain(), runtime.network(), fromBlock, toBlock, changed.size());
		}
		return changed.size();
	}

	private ScanCheckpointEntity initCheckpoint(long latestBlock, ChainRuntimeConfig runtime) {
		ScanCheckpointEntity checkpoint = new ScanCheckpointEntity();
		checkpoint.setChain(runtime.chain());
		checkpoint.setNetwork(runtime.network());
		long start = scannerProperties.getInitialStartBlock();
		if (start <= 0 || start > latestBlock) {
			checkpoint.setLastScannedBlock(Math.max(0L, latestBlock - 1));
		} else {
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
		} else if (watchedTopicIndex == 2) {
			filter.addNullTopic();
			filter.addSingleTopic(watchedAddressTopic);
		} else {
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
			if (logObject.getTopics().size() != 3 || !isValidUint256Hex(logObject.getData())) {
				continue;
			}

			long blockNumber = logObject.getBlockNumber().longValueExact();
			EthBlock.Block block = getBlock(web3j, blockNumber, blockCache);
			BigInteger amountValue = hexToBigInteger(logObject.getData());
			int confirmations = confirmations(latest, blockNumber);

			AssetEventEntity event = new AssetEventEntity();
			event.setChain(runtime.chain());
			event.setNetwork(runtime.network());
			event.setBlockNumber(blockNumber);
			event.setBlockHash(logObject.getBlockHash());
			event.setTxHash(logObject.getTransactionHash());
			event.setLogIndex(logObject.getLogIndex().intValue());
			event.setFromAddress(topicToAddress(logObject.getTopics().get(1)));
			event.setToAddress(topicToAddress(logObject.getTopics().get(2)));
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
				event.setAmount(tx.getValue().toString());
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

	private boolean isWatchedAddressTransfer(Transaction tx, Set<String> watchAddressSet) {
		String from = normalizeEvmAddress(tx.getFrom());
		String to = normalizeEvmAddress(tx.getTo());
		return (from != null && watchAddressSet.contains(from))
			|| (to != null && watchAddressSet.contains(to));
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
			existing.setChain(incoming.getChain());
			existing.setNetwork(incoming.getNetwork());
			existing.setBlockNumber(incoming.getBlockNumber());
			existing.setBlockHash(incoming.getBlockHash());
			existing.setFromAddress(incoming.getFromAddress());
			existing.setToAddress(incoming.getToAddress());
			existing.setTokenType(incoming.getTokenType());
			existing.setTokenContract(incoming.getTokenContract());
			existing.setSymbol(incoming.getSymbol());
			existing.setAmount(incoming.getAmount());
			existing.setDecimals(incoming.getDecimals());
			existing.setOccurredAt(incoming.getOccurredAt());
			existing.setIngestedAt(incoming.getIngestedAt());
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

	private boolean isValidUint256Hex(String data) {
		if (!StringUtils.hasText(data)) {
			return false;
		}
		String value = data.trim();
		if (!value.startsWith("0x") || value.length() != 66) {
			return false;
		}
		for (int i = 2; i < value.length(); i++) {
			char c = value.charAt(i);
			boolean digit = c >= '0' && c <= '9';
			boolean lowerHex = c >= 'a' && c <= 'f';
			boolean upperHex = c >= 'A' && c <= 'F';
			if (!digit && !lowerHex && !upperHex) {
				return false;
			}
		}
		return true;
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

	private boolean hasWatchers(RuntimeWatchers watchers) {
		return watchers != null
			&& (!watchers.watchAddressTopics().isEmpty() || !watchers.watchAddressSet().isEmpty());
	}

	private boolean isSolanaChain(String chain) {
		if (!StringUtils.hasText(chain)) {
			return false;
		}
		String normalized = chain.trim().toUpperCase();
		return "SOL".equals(normalized) || "SOLANA".equals(normalized);
	}

	private String normalizeEvmAddress(String address) {
		if (!StringUtils.hasText(address)) {
			return null;
		}
		String normalized = address.toLowerCase().trim();
		if (!normalized.startsWith("0x")) {
			normalized = "0x" + normalized;
		}
		return normalized.length() == 42 ? normalized : null;
	}

	@FunctionalInterface
	private interface RpcSupplier<T> {
		T get() throws IOException;
	}

	private record ScanWindow(long fromBlock, long toBlock) {
	}
}
