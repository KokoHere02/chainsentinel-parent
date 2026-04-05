package com.chainsentinel.infra.service;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.service.EventConfirmationService;
import com.chainsentinel.infra.config.ConfirmationProperties;
import com.chainsentinel.infra.config.ScannerProperties;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.repository.AssetEventRepository;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.protocol.http.HttpService;

@Service
public class DefaultEventConfirmationService implements EventConfirmationService {

private static final Logger log = LoggerFactory.getLogger(DefaultEventConfirmationService.class);

private final AssetEventRepository assetEventRepository;
private final ChainConfigRepository chainConfigRepository;
private final ScannerProperties scannerProperties;
private final ConfirmationProperties confirmationProperties;
private final ChainConfigRpcUrlCodec chainConfigRpcUrlCodec;

public DefaultEventConfirmationService(
AssetEventRepository assetEventRepository,
ChainConfigRepository chainConfigRepository,
ScannerProperties scannerProperties,
ConfirmationProperties confirmationProperties,
ChainConfigRpcUrlCodec chainConfigRpcUrlCodec
) {
this.assetEventRepository = assetEventRepository;
this.chainConfigRepository = chainConfigRepository;
this.scannerProperties = scannerProperties;
this.confirmationProperties = confirmationProperties;
this.chainConfigRpcUrlCodec = chainConfigRpcUrlCodec;
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

String rpcUrl = chainConfigRpcUrlCodec.decryptIfNeeded(chainConfig.getRpcUrl(), chain, network);
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
}

if (batch.size() < batchSize) {
break;
}
}

log.info("Confirmation advance finished: chain={}-{}, pending={}, updated={}, promoted={}, latestBlock={}",
chain, network, pendingCount, updated, promoted, latestBlock);
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