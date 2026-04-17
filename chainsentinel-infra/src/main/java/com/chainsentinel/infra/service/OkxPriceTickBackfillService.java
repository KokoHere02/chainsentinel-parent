package com.chainsentinel.infra.service;

import com.chainsentinel.infra.entity.PriceTickEntity;
import com.chainsentinel.infra.repository.PriceTickRepository;
import com.chainsentinel.price.provider.okx.OkxApiClient;
import com.chainsentinel.price.provider.okx.dto.OkxHistoryCandle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OkxPriceTickBackfillService {

	private static final Logger log = LoggerFactory.getLogger(OkxPriceTickBackfillService.class);
	private static final String PROVIDER_NAME = "okx_ws";
	private static final String INST_TYPE = "SPOT";

	private final OkxApiClient okxApiClient;
	private final PriceTickRepository priceTickRepository;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public OkxPriceTickBackfillService(OkxApiClient okxApiClient, PriceTickRepository priceTickRepository) {
		this.okxApiClient = okxApiClient;
		this.priceTickRepository = priceTickRepository;
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
		long start = System.currentTimeMillis();
		int totalFetched = 0;
		int totalInserted = 0;
		int rounds = 0;
		boolean reachedFrom = false;
		String stopReason = "unknown";
		Long lastOldestTs = null;
		Long lastNewestTs = null;
		Long nextCursorAfter = null;
		try {
			String normalizedInstId = normalizeInstId(instId);
			if (normalizedInstId == null) {
				throw new IllegalArgumentException("instId is required");
			}
			if (fromTs <= 0 || toTs <= 0 || fromTs > toTs) {
				throw new IllegalArgumentException("invalid from/to range");
			}
			String[] parts = parseInstId(normalizedInstId);
			Long cursorAfter = toTs;
			long lastOldestTsGuard = Long.MAX_VALUE;
			nextCursorAfter = cursorAfter;

			for (int i = 0; i < maxRounds; i++) {
				rounds++;
				List<OkxHistoryCandle> candles = okxApiClient.fetchHistoryCandles(normalizedInstId, bar, cursorAfter, pageLimit);
				if (candles.isEmpty()) {
					stopReason = "empty_batch";
					break;
				}
				totalFetched += candles.size();

				List<PriceTickEntity> entities = new ArrayList<>();
				long oldestTs = Long.MAX_VALUE;
				long newestTs = Long.MIN_VALUE;
				for (OkxHistoryCandle candle : candles) {
					if (candle == null || candle.ts() <= 0 || candle.closePrice() == null) {
						continue;
					}
					if (candle.ts() < oldestTs) {
						oldestTs = candle.ts();
					}
					if (candle.ts() > newestTs) {
						newestTs = candle.ts();
					}
					if (candle.ts() < fromTs || candle.ts() > toTs) {
						continue;
					}
					PriceTickEntity entity = new PriceTickEntity();
					entity.setProviderName(PROVIDER_NAME);
					entity.setInstType(INST_TYPE);
					entity.setInstId(normalizedInstId);
					entity.setBaseSymbol(parts[0]);
					entity.setQuoteSymbol(parts[1]);
					entity.setPrice(candle.closePrice());
					entity.setQuoteTs(candle.ts());
					entities.add(entity);
				}

				int insertedThisRound = saveIgnoreDuplicate(entities);
				totalInserted += insertedThisRound;
				lastOldestTs = oldestTs == Long.MAX_VALUE ? null : oldestTs;
				lastNewestTs = newestTs == Long.MIN_VALUE ? null : newestTs;

				log.info(
					"price.tick.backfill.okx.round instId={} round={}/{} cursorAfter={} fetched={} inRange={} inserted={} oldestTs={} newestTs={}",
					normalizedInstId,
					rounds,
					maxRounds,
					cursorAfter,
					candles.size(),
					entities.size(),
					insertedThisRound,
					lastOldestTs,
					lastNewestTs
				);

				if (oldestTs == Long.MAX_VALUE) {
					stopReason = "all_rows_invalid";
					break;
				}
				if (oldestTs <= fromTs) {
					reachedFrom = true;
					stopReason = "reached_from";
					break;
				}
				if (oldestTs >= lastOldestTsGuard) {
					stopReason = "cursor_stuck";
					break;
				}

				lastOldestTsGuard = oldestTs;
				cursorAfter = Math.max(1L, oldestTs - 1L);
				nextCursorAfter = cursorAfter;
				if (sleepMs > 0) {
					try {
						Thread.sleep(sleepMs);
					} catch (InterruptedException ignored) {
						Thread.currentThread().interrupt();
						stopReason = "interrupted";
						break;
					}
				}
			}

			if ("unknown".equals(stopReason)) {
				stopReason = "max_rounds_reached";
			}

			long durationMs = System.currentTimeMillis() - start;
			log.info("price.tick.backfill.okx.done instId={} from={} to={} bar={} rounds={} fetched={} inserted={} reachedFrom={} stopReason={} lastOldestTs={} lastNewestTs={} nextCursorAfter={} durationMs={}",
				normalizedInstId, fromTs, toTs, bar, rounds, totalFetched, totalInserted, reachedFrom, stopReason,
				lastOldestTs, lastNewestTs, nextCursorAfter, durationMs);
			return new BackfillResult(
				normalizedInstId,
				fromTs,
				toTs,
				bar,
				rounds,
				totalFetched,
				totalInserted,
				reachedFrom,
				stopReason,
				lastOldestTs,
				lastNewestTs,
				nextCursorAfter,
				Instant.ofEpochMilli(start),
				Instant.now()
			);
		} finally {
			running.set(false);
		}
	}

	@Transactional
	protected int saveIgnoreDuplicate(List<PriceTickEntity> entities) {
		if (entities == null || entities.isEmpty()) {
			return 0;
		}
		int inserted = 0;
		for (PriceTickEntity entity : entities) {
			try {
				priceTickRepository.save(entity);
				inserted++;
			} catch (DataIntegrityViolationException ignore) {
				// duplicate quoteTs for same provider+inst
			}
		}
		return inserted;
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