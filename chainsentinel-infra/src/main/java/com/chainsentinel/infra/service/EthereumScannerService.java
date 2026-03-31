package com.chainsentinel.infra.service;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.model.TokenType;
import com.chainsentinel.core.service.ScannerService;
import com.chainsentinel.infra.config.ScannerProperties;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.entity.MonitorAddressEntity;
import com.chainsentinel.infra.entity.ScanCheckpointEntity;
import com.chainsentinel.infra.repository.AssetEventRepository;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import com.chainsentinel.infra.repository.ScanCheckpointRepository;
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
    private final AddressAlertMatcher addressAlertMatcher;

    public EthereumScannerService(
            ScannerProperties scannerProperties,
            ChainConfigRepository chainConfigRepository,
            ScanCheckpointRepository scanCheckpointRepository,
            AssetEventRepository assetEventRepository,
            MonitorAddressRepository monitorAddressRepository,
            AddressAlertMatcher addressAlertMatcher
    ) {
        this.scannerProperties = scannerProperties;
        this.chainConfigRepository = chainConfigRepository;
        this.scanCheckpointRepository = scanCheckpointRepository;
        this.assetEventRepository = assetEventRepository;
        this.monitorAddressRepository = monitorAddressRepository;
        this.addressAlertMatcher = addressAlertMatcher;
    }

    @Override
    public int runOnce(boolean full) {
        Set<String> monitorChains = resolveMonitoredChains();
        if (monitorChains.isEmpty()) {
            log.info("No enabled monitor addresses, skip scanning");
            return 0;
        }

        int totalInserted = 0;
        for (String chain : monitorChains) {
            List<ChainConfigEntity> configs = chainConfigRepository.findByChainAndEnabledTrue(chain);
            if (configs.isEmpty()) {
                log.info("No enabled chain_config for chain {}, skip", chain);
                continue;
            }
            for (ChainConfigEntity cfg : configs) {
                ChainRuntimeConfig runtime = toRuntimeConfig(cfg);
                if (!StringUtils.hasText(runtime.rpcUrl())) {
                    log.warn("Skip chain {}-{}: rpcUrl is empty", runtime.chain(), runtime.network());
                    continue;
                }
                totalInserted += runOnceForRuntime(runtime, full);
            }
        }
        return totalInserted;
    }

    private int runOnceForRuntime(ChainRuntimeConfig runtime, boolean full) {
        Web3j web3j = Web3j.build(new HttpService(runtime.rpcUrl()));
        try {
            long latest = rpcCallWithRetry("eth_blockNumber", () ->
              web3j.ethBlockNumber().send().getBlockNumber().longValueExact()
            );
            ScanWindow window = resolveWindow(latest, runtime);
            if (window.fromBlock() > window.toBlock()) {
                return 0;
            }

            int inserted = 0;
            Map<Long, EthBlock.Block> blockCache = new HashMap<>();
            inserted += ingestErc20TransferLogs(web3j, latest, window.fromBlock(), window.toBlock(), blockCache, runtime, full);
            inserted += ingestEthTransfers(web3j, latest, window.fromBlock(), window.toBlock(), blockCache, runtime, full);

            saveCheckpoint(window.toBlock(), runtime);
            log.info("Scan completed: chain={}-{}, window=[{}-{}], inserted={}, full={}",
                    runtime.chain(), runtime.network(), window.fromBlock(), window.toBlock(), inserted, full);
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

    private Set<String> resolveMonitoredChains() {
        Set<String> chains = new HashSet<>();
        for (MonitorAddressEntity item : monitorAddressRepository.findByEnabledTrue()) {
            if (StringUtils.hasText(item.getChain())) {
                chains.add(item.getChain().trim().toUpperCase());
            }
        }
        return chains;
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
            boolean full
    ) throws IOException {
        if (full) {
            return ingestErc20TransferLogsFullRange(web3j, latest, from, to, blockCache, runtime);
        }

        List<String> watchAddressTopics = resolveWatchAddressTopics(runtime.chain());
        if (watchAddressTopics.isEmpty()) {
            log.info("No enabled monitor addresses for chain {}, skip ERC20 log scan", runtime.chain());
            return 0;
        }

        int inserted = 0;
        for (String watchAddressTopic : watchAddressTopics) {
            inserted += ingestErc20TransferLogsByAddressRange(web3j, latest, from, to, blockCache, runtime, 1, watchAddressTopic);
            inserted += ingestErc20TransferLogsByAddressRange(web3j, latest, from, to, blockCache, runtime, 2, watchAddressTopic);
        }
        return inserted;
    }

    private int ingestErc20TransferLogsFullRange(
            Web3j web3j,
            long latest,
            long from,
            long to,
            Map<Long, EthBlock.Block> blockCache,
            ChainRuntimeConfig runtime
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
        EthLog logs = rpcCallWithRetry("eth_getLogs", () -> web3j.ethGetLogs(filter).send());

        if (isTooManyLogsResponse(logs)) {
            if (from == to) {
                log.warn("Skip block {} for ERC20 logs due to provider result-size limit: {}",
                        from, logs.getError() == null ? "unknown" : logs.getError().getMessage());
                return 0;
            }
            long mid = from + (to - from) / 2;
            int left = ingestErc20TransferLogsFullRange(web3j, latest, from, mid, blockCache, runtime);
            int right = ingestErc20TransferLogsFullRange(web3j, latest, mid + 1, to, blockCache, runtime);
            return left + right;
        }

        return ingestErc20TransferLogResults(logs.getLogs(), web3j, latest, blockCache, runtime);
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
            int left = ingestErc20TransferLogsByAddressRange(web3j, latest, from, mid, blockCache, runtime, watchedTopicIndex, watchedAddressTopic);
            int right = ingestErc20TransferLogsByAddressRange(web3j, latest, mid + 1, to, blockCache, runtime, watchedTopicIndex, watchedAddressTopic);
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
            boolean full
    ) throws IOException {
        Set<String> watchAddressSet = resolveWatchAddressSet(runtime.chain());
        if (!full && watchAddressSet.isEmpty()) {
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
                if (!full && !isWatchedAddressTransfer(tx, watchAddressSet)) {
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
                BigInteger amountValue = tx.getValue();                event.setAmount(amountValue.toString());
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

    private List<String> resolveWatchAddressTopics(String chain) {
        return monitorAddressRepository.findByChainAndEnabledTrue(chain).stream()
                .map(MonitorAddressEntity::getAddress)
                .map(this::addressToTopic)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private Set<String> resolveWatchAddressSet(String chain) {
        Set<String> watchAddresses = new HashSet<>();
        for (MonitorAddressEntity item : monitorAddressRepository.findByChainAndEnabledTrue(chain)) {
            String normalized = normalizeAddress(item.getAddress());
            if (normalized != null) {
                watchAddresses.add(normalized);
            }
        }
        return watchAddresses;
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
        EthBlock.Block loaded = rpcCallWithRetry("eth_getBlockByNumber:" + blockNumber, () -> web3j.ethGetBlockByNumber(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)),
                true
        ).send().getBlock());
        cache.put(blockNumber, loaded);
        return loaded;
    }

    private int upsertEvent(AssetEventEntity incoming, boolean evaluateAlert) {
        Optional<AssetEventEntity> existingOpt = assetEventRepository.findByChainAndTxHashAndLogIndex(
                incoming.getChain(), incoming.getTxHash(), incoming.getLogIndex());
        if (existingOpt.isPresent()) {
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
}














