package com.chainsentinel.infra.service;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.model.TokenType;
import com.chainsentinel.core.service.ScannerService;
import com.chainsentinel.infra.config.ScannerProperties;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.entity.ScanCheckpointEntity;
import com.chainsentinel.infra.repository.AssetEventRepository;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import com.chainsentinel.infra.repository.ScanCheckpointRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import org.web3j.protocol.http.HttpService;

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
    private final AddressAlertMatcher addressAlertMatcher;

    public EthereumScannerService(
            ScannerProperties scannerProperties,
            ChainConfigRepository chainConfigRepository,
            ScanCheckpointRepository scanCheckpointRepository,
            AssetEventRepository assetEventRepository,
            AddressAlertMatcher addressAlertMatcher
    ) {
        this.scannerProperties = scannerProperties;
        this.chainConfigRepository = chainConfigRepository;
        this.scanCheckpointRepository = scanCheckpointRepository;
        this.assetEventRepository = assetEventRepository;
        this.addressAlertMatcher = addressAlertMatcher;
    }

    @Override
    @Transactional
    public int runOnce() {
        ChainRuntimeConfig runtime = resolveRuntimeConfig();
        if (!runtime.enabled()) {
            log.info("Scanner skipped: chain {}-{} disabled", runtime.chain(), runtime.network());
            return 0;
        }
        if (!StringUtils.hasText(runtime.rpcUrl())) {
            throw new IllegalStateException("No rpcUrl configured. Set chainsentinel.scanner.rpc-url or /api/chains config.");
        }

        Web3j web3j = Web3j.build(new HttpService(runtime.rpcUrl()));
        try {
            long latest = web3j.ethBlockNumber().send().getBlockNumber().longValueExact();
            ScanWindow window = resolveWindow(latest, runtime);
            if (window.fromBlock() > window.toBlock()) {
                return 0;
            }

            int inserted = 0;
            Map<Long, EthBlock.Block> blockCache = new HashMap<>();
            inserted += ingestErc20TransferLogs(web3j, latest, window.fromBlock(), window.toBlock(), blockCache, runtime);
            inserted += ingestEthTransfers(web3j, latest, window.fromBlock(), window.toBlock(), blockCache, runtime);

            saveCheckpoint(window.toBlock(), runtime);
            log.info("Scan completed: [{}-{}], inserted={}", window.fromBlock(), window.toBlock(), inserted);
            return inserted;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan Ethereum logs", e);
        } finally {
            web3j.shutdown();
        }
    }

    private ChainRuntimeConfig resolveRuntimeConfig() {
        Optional<ChainConfigEntity> fromDb = chainConfigRepository
                .findByChainAndNetwork(scannerProperties.getChain(), scannerProperties.getNetwork());

        if (fromDb.isPresent()) {
            ChainConfigEntity cfg = fromDb.get();
            return new ChainRuntimeConfig(
                    cfg.getChain(),
                    cfg.getNetwork(),
                    cfg.getRpcUrl(),
                    cfg.getConfirmRequired(),
                    Boolean.TRUE.equals(cfg.getEnabled())
            );
        }

        return new ChainRuntimeConfig(
                scannerProperties.getChain(),
                scannerProperties.getNetwork(),
                scannerProperties.getRpcUrl(),
                scannerProperties.getConfirmRequired(),
                scannerProperties.isEnabled()
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
            ChainRuntimeConfig runtime
    ) throws IOException {
        EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(from)),
                DefaultBlockParameter.valueOf(BigInteger.valueOf(to)),
                List.of()
        );
        filter.addSingleTopic(ERC20_TRANSFER_TOPIC);
        EthLog logs = web3j.ethGetLogs(filter).send();

        int inserted = 0;
        for (EthLog.LogResult<?> result : logs.getLogs()) {
            EthLog.LogObject logObject = (EthLog.LogObject) result.get();
            if (logObject.getTopics().size() < 3) {
                continue;
            }
            long blockNumber = logObject.getBlockNumber().longValueExact();
            EthBlock.Block block = getBlock(web3j, blockNumber, blockCache);

            String fromAddress = topicToAddress(logObject.getTopics().get(1));
            String toAddress = topicToAddress(logObject.getTopics().get(2));
            BigDecimal amount = new BigDecimal(hexToBigInteger(logObject.getData()));
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
            event.setAmount(amount);
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
            ChainRuntimeConfig runtime
    ) throws IOException {
        int inserted = 0;
        for (long blockNumber = from; blockNumber <= to; blockNumber++) {
            EthBlock.Block block = getBlock(web3j, blockNumber, blockCache);
            Instant occurredAt = Instant.ofEpochSecond(block.getTimestamp().longValueExact());
            for (EthBlock.TransactionResult<?> txResult : block.getTransactions()) {
                Transaction tx = (Transaction) txResult.get();
                if (tx.getValue() == null || tx.getValue().signum() <= 0 || tx.getTo() == null) {
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
                event.setAmount(new BigDecimal(tx.getValue()));
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

    private EthBlock.Block getBlock(Web3j web3j, long blockNumber, Map<Long, EthBlock.Block> cache) throws IOException {
        EthBlock.Block block = cache.get(blockNumber);
        if (block != null) {
            return block;
        }
        EthBlock.Block loaded = web3j.ethGetBlockByNumber(
                        DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)),
                        true
                )
                .send()
                .getBlock();
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

    private record ScanWindow(long fromBlock, long toBlock) {
    }

    private record ChainRuntimeConfig(String chain, String network, String rpcUrl, int confirmRequired, boolean enabled) {
    }
}

