package com.chainsentinel.infra.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PriceTickBackfillDispatchService {

	private static final Logger log = LoggerFactory.getLogger(PriceTickBackfillDispatchService.class);
	private static final int DEFAULT_RETENTION_DAYS = 30;
	private static final String DEFAULT_BAR = "1m";
	private static final int DEFAULT_PAGE_LIMIT = 300;
	private static final int DEFAULT_MAX_ROUNDS = 1000;
	private static final long DEFAULT_SLEEP_MS = 50L;

	private final OkxPriceTickBackfillService okxPriceTickBackfillService;
	private final Executor backfillExecutor;
	private final Set<String> pendingInstIds = ConcurrentHashMap.newKeySet();

	public PriceTickBackfillDispatchService(
		OkxPriceTickBackfillService okxPriceTickBackfillService,
		@Qualifier("priceTickBackfillExecutor") Executor backfillExecutor
	) {
		this.okxPriceTickBackfillService = okxPriceTickBackfillService;
		this.backfillExecutor = backfillExecutor;
	}

	public void submitLast30Days(String instId, String trigger) {
		String normalizedInstId = normalizeInstId(instId);
		if (normalizedInstId == null) {
			return;
		}
		if (!pendingInstIds.add(normalizedInstId)) {
			log.info("price.tick.backfill.skip reason=pending instId={} trigger={}", normalizedInstId, trigger);
			return;
		}
		backfillExecutor.execute(() -> {
			try {
				long toTs = System.currentTimeMillis();
				long fromTs = Instant.ofEpochMilli(toTs)
					.minus(DEFAULT_RETENTION_DAYS, ChronoUnit.DAYS)
					.toEpochMilli();
				log.info("price.tick.backfill.enqueue instId={} trigger={} from={} to={}", normalizedInstId, trigger, fromTs, toTs);
				okxPriceTickBackfillService.backfill(
					normalizedInstId,
					fromTs,
					toTs,
					DEFAULT_BAR,
					DEFAULT_PAGE_LIMIT,
					DEFAULT_MAX_ROUNDS,
					DEFAULT_SLEEP_MS
				);
			} catch (Exception ex) {
				log.warn("price.tick.backfill.async.failed instId={} trigger={} error={}", normalizedInstId, trigger, ex.getMessage());
			} finally {
				pendingInstIds.remove(normalizedInstId);
			}
		});
	}

	private String normalizeInstId(String instId) {
		if (!StringUtils.hasText(instId)) {
			return null;
		}
		return instId.trim().toUpperCase(Locale.ROOT);
	}
}