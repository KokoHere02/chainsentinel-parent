package com.chainsentinel.infra.service;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.model.TokenType;
import com.chainsentinel.infra.config.ScannerProperties;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.entity.ScanCheckpointEntity;
import com.chainsentinel.infra.repository.AssetEventRepository;
import com.chainsentinel.infra.repository.ScanCheckpointRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SolanaEventScanner implements ChainEventScanner {

	private static final Logger log = LoggerFactory.getLogger(SolanaEventScanner.class);

	private final ScannerProperties scannerProperties;
	private final ScanCheckpointRepository scanCheckpointRepository;
	private final AssetEventRepository assetEventRepository;
	private final AddressAlertMatcher addressAlertMatcher;
	private final SolanaRpcService solanaRpcService;

	public SolanaEventScanner(
		ScannerProperties scannerProperties,
		ScanCheckpointRepository scanCheckpointRepository,
		AssetEventRepository assetEventRepository,
		AddressAlertMatcher addressAlertMatcher,
		SolanaRpcService solanaRpcService
	) {
		this.scannerProperties = scannerProperties;
		this.scanCheckpointRepository = scanCheckpointRepository;
		this.assetEventRepository = assetEventRepository;
		this.addressAlertMatcher = addressAlertMatcher;
		this.solanaRpcService = solanaRpcService;
	}

	@Override
	public boolean supports(String chain) {
		if (chain == null || chain.isBlank()) {
			return false;
		}
		String normalized = chain.trim().toUpperCase();
		return "SOL".equals(normalized) || "SOLANA".equals(normalized);
	}

	@Override
	public int scan(ChainRuntimeConfig runtime, RuntimeWatchers watchers) {
		try {
			long latest = rpcCallWithRetry("sol_getSlot", () -> solanaRpcService.getLatestSlot(runtime.rpcUrl()));
			ScanWindow window = resolveWindow(latest, runtime);
			if (window.fromSlot() > window.toSlot()) {
				return 0;
			}

			int inserted = 0;
			for (String watchAddress : watchers.watchAddressSet()) {
				inserted += ingestSolanaAddressSignatures(runtime, watchAddress, latest, window.fromSlot(), window.toSlot());
			}

			saveCheckpoint(window.toSlot(), runtime);
			log.info("Solana scan completed: chain={}-{}, window=[{}-{}], inserted={}",
				runtime.chain(), runtime.network(), window.fromSlot(), window.toSlot(), inserted);
			return inserted;
		} catch (Exception ex) {
			log.error("Solana scan failed for chain {}-{}", runtime.chain(), runtime.network(), ex);
			return 0;
		}
	}

	private int ingestSolanaAddressSignatures(
		ChainRuntimeConfig runtime,
		String watchAddress,
		long latestSlot,
		long fromSlot,
		long toSlot
	) throws IOException {
		int limit = Math.max(1, Math.min(1_000, scannerProperties.getWindowSize() * 2));
		var signatures = rpcCallWithRetry("sol_getSignaturesForAddress", () ->
			solanaRpcService.getSignaturesForAddress(runtime.rpcUrl(), watchAddress, limit)
		);
		if (signatures.isEmpty()) {
			return 0;
		}

		int inserted = 0;
		for (var sig : signatures) {
			if (sig.slot() < fromSlot || sig.slot() > toSlot || !sig.success()) {
				continue;
			}
			var transfers = rpcCallWithRetry("sol_getTransaction", () ->
				solanaRpcService.getTransfersBySignature(runtime.rpcUrl(), sig.signature())
			);

			for (var transfer : transfers.nativeTransfers()) {
				if (!watchAddress.equals(transfer.source()) && !watchAddress.equals(transfer.destination())) {
					continue;
				}
				int confirmations = confirmations(latestSlot, transfer.slot());
				AssetEventEntity event = new AssetEventEntity();
				event.setChain(runtime.chain());
				event.setNetwork(runtime.network());
				event.setBlockNumber(transfer.slot());
				event.setBlockHash(null);
				event.setTxHash(transfer.signature());
				event.setLogIndex(transfer.logIndex());
				event.setFromAddress(transfer.source());
				event.setToAddress(transfer.destination());
				event.setTokenType(TokenType.SOL);
				event.setTokenContract("NATIVE");
				event.setSymbol("SOL");
				event.setAmount(transfer.lamports().toString());
				event.setDecimals(9);
				event.setConfirmations(confirmations);
				event.setStatus(statusByConfirmations(confirmations, runtime));
				event.setOccurredAt(transfer.occurredAt() == null ? Instant.now() : transfer.occurredAt());
				event.setIngestedAt(Instant.now());
				inserted += upsertEvent(event, true);
			}

			for (var transfer : transfers.tokenTransfers()) {
				if (!watchAddress.equals(transfer.source()) && !watchAddress.equals(transfer.destination())) {
					continue;
				}
				int confirmations = confirmations(latestSlot, transfer.slot());
				AssetEventEntity event = new AssetEventEntity();
				event.setChain(runtime.chain());
				event.setNetwork(runtime.network());
				event.setBlockNumber(transfer.slot());
				event.setBlockHash(null);
				event.setTxHash(transfer.signature());
				event.setLogIndex(1_000_000 + transfer.logIndex());
				event.setFromAddress(transfer.source());
				event.setToAddress(transfer.destination());
				event.setTokenType(TokenType.SPL);
				event.setTokenContract(transfer.mint());
				event.setSymbol(null);
				event.setAmount(transfer.amount().toString());
				event.setDecimals(transfer.decimals());
				event.setConfirmations(confirmations);
				event.setStatus(statusByConfirmations(confirmations, runtime));
				event.setOccurredAt(transfer.occurredAt() == null ? Instant.now() : transfer.occurredAt());
				event.setIngestedAt(Instant.now());
				inserted += upsertEvent(event, true);
			}
		}
		return inserted;
	}

	private ScanWindow resolveWindow(long latestSlot, ChainRuntimeConfig runtime) {
		ScanCheckpointEntity checkpoint = scanCheckpointRepository
			.findByChainAndNetwork(runtime.chain(), runtime.network())
			.orElseGet(() -> initCheckpoint(latestSlot, runtime));
		long lookback = Math.max(0L, scannerProperties.getReorgLookbackBlocks());
		long from = Math.max(0L, checkpoint.getLastScannedBlock() + 1 - lookback);
		long to = Math.min(from + scannerProperties.getWindowSize() - 1L, latestSlot);
		return new ScanWindow(from, to);
	}

	private ScanCheckpointEntity initCheckpoint(long latestSlot, ChainRuntimeConfig runtime) {
		ScanCheckpointEntity checkpoint = new ScanCheckpointEntity();
		checkpoint.setChain(runtime.chain());
		checkpoint.setNetwork(runtime.network());
		long start = scannerProperties.getInitialStartBlock();
		if (start <= 0 || start > latestSlot) {
			checkpoint.setLastScannedBlock(Math.max(0L, latestSlot - 1));
		} else {
			checkpoint.setLastScannedBlock(Math.max(0L, start - 1));
		}
		return scanCheckpointRepository.save(checkpoint);
	}

	private void saveCheckpoint(long slot, ChainRuntimeConfig runtime) {
		ScanCheckpointEntity checkpoint = scanCheckpointRepository
			.findByChainAndNetwork(runtime.chain(), runtime.network())
			.orElseThrow(() -> new IllegalStateException("Checkpoint must exist"));
		checkpoint.setLastScannedBlock(slot);
		scanCheckpointRepository.save(checkpoint);
	}

	private EventStatus statusByConfirmations(int confirmations, ChainRuntimeConfig runtime) {
		return confirmations >= runtime.confirmRequired() ? EventStatus.CONFIRMED : EventStatus.PENDING;
	}

	private int confirmations(long latest, long slot) {
		return (int) (latest - slot + 1);
	}

	private int upsertEvent(AssetEventEntity incoming, boolean evaluateAlert) {
		Optional<AssetEventEntity> existingOpt = assetEventRepository.findByChainAndTxHashAndLogIndex(
			incoming.getChain(), incoming.getTxHash(), incoming.getLogIndex());
		if (existingOpt.isPresent()) {
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

	private <T> T rpcCallWithRetry(String operation, RpcSupplier<T> supplier) throws IOException {
		int maxRetries = Math.max(0, scannerProperties.getRpcRetryMax());
		long baseBackoffMs = Math.max(0L, scannerProperties.getRpcRetryBackoffMs());

		int attempt = 0;
		while (true) {
			try {
				return supplier.get();
			} catch (IOException | RuntimeException ex) {
				if (attempt >= maxRetries) {
					if (ex instanceof IOException io) {
						throw io;
					}
					throw ex;
				}
				long sleepMs = baseBackoffMs * (1L << attempt);
				log.warn("SOL RPC call failed (operation={}, attempt={}/{}), retry in {} ms: {}",
					operation, attempt + 1, maxRetries + 1, sleepMs, ex.getMessage());
				sleepQuietly(sleepMs);
				attempt++;
			}
		}
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

	private record ScanWindow(long fromSlot, long toSlot) {
	}
}
