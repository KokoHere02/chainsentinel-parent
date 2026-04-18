package com.chainsentinel.infra.service;

import com.chainsentinel.infra.entity.PriceTickEntity;
import com.chainsentinel.price.provider.okx.OkxApiClient;
import com.chainsentinel.price.provider.okx.dto.OkxHistoryCandle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OkxPriceTickBackfillService {

	private static final Logger log = LoggerFactory.getLogger(OkxPriceTickBackfillService.class);
	private static final String PROVIDER_NAME = "okx_ws";
	private static final String INST_TYPE = "SPOT";

	private final OkxApiClient okxApiClient;
	private final PriceTickPersistenceService priceTickPersistenceService;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public OkxPriceTickBackfillService(
		OkxApiClient okxApiClient,
		PriceTickPersistenceService priceTickPersistenceService
	) {
		this.okxApiClient = okxApiClient;
		this.priceTickPersistenceService = priceTickPersistenceService;
	}

	public BackfillResult backfill(
		String instId,
		long fromTs,
		long toTs,
		String bar,
		int pageLimit,
		int maxRounds,
		long sleepMs
	) {
		if (!running.compareAndSet(false, true)) {
			throw new IllegalStateException("backfill already running");
		}
		long startedAtMs = System.currentTimeMillis();
		try {
			NormalizedRequest request = buildRequest(instId, fromTs, toTs, bar, pageLimit, maxRounds, sleepMs);
			BackfillState state = BackfillState.startWith(request.toTs());
			runRounds(request, state);
			finalizeStopReason(state);
			return toResult(request, startedAtMs, state);
		} finally {
			running.set(false);
		}
	}

	private NormalizedRequest buildRequest(
		String instId,
		long fromTs,
		long toTs,
		String bar,
		int pageLimit,
		int maxRounds,
		long sleepMs
	) {
		String normalizedInstId = normalizeInstId(instId);
		if (normalizedInstId == null) {
			throw new IllegalArgumentException("instId is required");
		}
		if (fromTs <= 0 || toTs <= 0 || fromTs > toTs) {
			throw new IllegalArgumentException("invalid from/to range");
		}
		return new NormalizedRequest(
			normalizedInstId,
			fromTs,
			toTs,
			bar,
			pageLimit,
			maxRounds,
			sleepMs,
			parseInstId(normalizedInstId)
		);
	}

	private void runRounds(NormalizedRequest request, BackfillState state) {
		for (int i = 0; i < request.maxRounds(); i++) {
			if (!processSingleRound(request, state)) {
				return;
			}
		}
	}

	private boolean processSingleRound(NormalizedRequest request, BackfillState state) {
		state.rounds++;
		List<OkxHistoryCandle> candles = okxApiClient.fetchHistoryCandles(
			request.normalizedInstId(),
			request.bar(),
			state.cursorAfter,
			request.pageLimit()
		);
		if (candles.isEmpty()) {
			state.stopReason = "empty_batch";
			return false;
		}
		state.totalFetched += candles.size();

		RoundPreparedData prepared = prepareRoundData(request, candles);
		int insertedThisRound = priceTickPersistenceService.saveIgnoreDuplicate(prepared.entities());
		state.totalInserted += insertedThisRound;
		state.lastOldestTs = prepared.oldestTs() == Long.MAX_VALUE ? null : prepared.oldestTs();
		state.lastNewestTs = prepared.newestTs() == Long.MIN_VALUE ? null : prepared.newestTs();

		logRound(request, state, candles.size(), prepared.entities().size(), insertedThisRound);
		if (shouldStopAfterRound(request, state, prepared.oldestTs())) {
			return false;
		}
		advanceCursor(state, prepared.oldestTs());
		return !sleepInterrupted(request.sleepMs(), state);
	}

	private RoundPreparedData prepareRoundData(NormalizedRequest request, List<OkxHistoryCandle> candles) {
		List<PriceTickEntity> entities = new ArrayList<>();
		long oldestTs = Long.MAX_VALUE;
		long newestTs = Long.MIN_VALUE;
		for (OkxHistoryCandle candle : candles) {
			if (candle == null || candle.ts() <= 0 || candle.closePrice() == null) {
				continue;
			}
			oldestTs = Math.min(oldestTs, candle.ts());
			newestTs = Math.max(newestTs, candle.ts());
			if (candle.ts() < request.fromTs() || candle.ts() > request.toTs()) {
				continue;
			}
			entities.add(toPriceTickEntity(request, candle));
		}
		return new RoundPreparedData(entities, oldestTs, newestTs);
	}

	private PriceTickEntity toPriceTickEntity(NormalizedRequest request, OkxHistoryCandle candle) {
		PriceTickEntity entity = new PriceTickEntity();
		entity.setProviderName(PROVIDER_NAME);
		entity.setInstType(INST_TYPE);
		entity.setInstId(request.normalizedInstId());
		entity.setBaseSymbol(request.parts()[0]);
		entity.setQuoteSymbol(request.parts()[1]);
		entity.setPrice(candle.closePrice());
		entity.setQuoteTs(candle.ts());
		return entity;
	}

	private void logRound(
		NormalizedRequest request,
		BackfillState state,
		int fetched,
		int inRange,
		int insertedThisRound
	) {
		log.info(
			"price.tick.backfill.okx.round instId={} round={}/{} cursorAfter={} fetched={} inRange={} inserted={} oldestTs={} newestTs={}",
			request.normalizedInstId(),
			state.rounds,
			request.maxRounds(),
			state.cursorAfter,
			fetched,
			inRange,
			insertedThisRound,
			state.lastOldestTs,
			state.lastNewestTs
		);
	}

	private boolean shouldStopAfterRound(NormalizedRequest request, BackfillState state, long oldestTs) {
		if (oldestTs == Long.MAX_VALUE) {
			state.stopReason = "all_rows_invalid";
			return true;
		}
		if (oldestTs <= request.fromTs()) {
			state.reachedFrom = true;
			state.stopReason = "reached_from";
			return true;
		}
		if (oldestTs >= state.lastOldestTsGuard) {
			state.stopReason = "cursor_stuck";
			return true;
		}
		return false;
	}

	private void advanceCursor(BackfillState state, long oldestTs) {
		state.lastOldestTsGuard = oldestTs;
		state.cursorAfter = Math.max(1L, oldestTs - 1L);
		state.nextCursorAfter = state.cursorAfter;
	}

	private boolean sleepInterrupted(long sleepMs, BackfillState state) {
		if (sleepMs <= 0) {
			return false;
		}
		try {
			Thread.sleep(sleepMs);
			return false;
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
			state.stopReason = "interrupted";
			return true;
		}
	}

	private void finalizeStopReason(BackfillState state) {
		if ("unknown".equals(state.stopReason)) {
			state.stopReason = "max_rounds_reached";
		}
	}

	private BackfillResult toResult(NormalizedRequest request, long startedAtMs, BackfillState state) {
		long durationMs = System.currentTimeMillis() - startedAtMs;
		log.info(
			"price.tick.backfill.okx.done instId={} from={} to={} bar={} rounds={} fetched={} inserted={} reachedFrom={} stopReason={} lastOldestTs={} lastNewestTs={} nextCursorAfter={} durationMs={}",
			request.normalizedInstId(),
			request.fromTs(),
			request.toTs(),
			request.bar(),
			state.rounds,
			state.totalFetched,
			state.totalInserted,
			state.reachedFrom,
			state.stopReason,
			state.lastOldestTs,
			state.lastNewestTs,
			state.nextCursorAfter,
			durationMs
		);
		return new BackfillResult(
			request.normalizedInstId(),
			request.fromTs(),
			request.toTs(),
			request.bar(),
			state.rounds,
			state.totalFetched,
			state.totalInserted,
			state.reachedFrom,
			state.stopReason,
			state.lastOldestTs,
			state.lastNewestTs,
			state.nextCursorAfter,
			Instant.ofEpochMilli(startedAtMs),
			Instant.now()
		);
	}

	private String normalizeInstId(String instId) {
		if (instId == null || instId.isBlank()) {
			return null;
		}
		return instId.trim().toUpperCase(Locale.ROOT);
	}

	private String[] parseInstId(String instId) {
		String[] parts = instId.split("-");
		if (parts.length < 2) {
			return new String[] {instId, "USDT"};
		}
		return new String[] {parts[0], parts[1]};
	}

	private record NormalizedRequest(
		String normalizedInstId,
		long fromTs,
		long toTs,
		String bar,
		int pageLimit,
		int maxRounds,
		long sleepMs,
		String[] parts
	) {
	}

	private record RoundPreparedData(
		List<PriceTickEntity> entities,
		long oldestTs,
		long newestTs
	) {
	}

	private static class BackfillState {
		private int totalFetched;
		private int totalInserted;
		private int rounds;
		private boolean reachedFrom;
		private String stopReason = "unknown";
		private Long lastOldestTs;
		private Long lastNewestTs;
		private Long nextCursorAfter;
		private long lastOldestTsGuard = Long.MAX_VALUE;
		private long cursorAfter;

		private static BackfillState startWith(long toTs) {
			BackfillState state = new BackfillState();
			state.cursorAfter = toTs;
			state.nextCursorAfter = toTs;
			return state;
		}
	}

	public record BackfillResult(
		String instId,
		long fromTs,
		long toTs,
		String bar,
		int rounds,
		int fetched,
		int inserted,
		boolean reachedFrom,
		String stopReason,
		Long lastOldestTs,
		Long lastNewestTs,
		Long nextCursorAfter,
		Instant startedAt,
		Instant finishedAt
	) {
	}
}
