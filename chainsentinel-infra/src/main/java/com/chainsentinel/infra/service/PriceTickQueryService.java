package com.chainsentinel.infra.service;

import com.chainsentinel.infra.entity.PriceTickEntity;
import com.chainsentinel.infra.repository.PriceTickRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PriceTickQueryService {

	private static final Logger log = LoggerFactory.getLogger(PriceTickQueryService.class);
	private static final long AGGREGATE_SLOW_LOG_THRESHOLD_MS = 200L;

	private final PriceTickRepository priceTickRepository;

	public PriceTickQueryService(PriceTickRepository priceTickRepository) {
		this.priceTickRepository = priceTickRepository;
	}

	public List<PriceTickView> query(
		String providerName,
		String instId,
		Long fromTs,
		Long toTs,
		int limit
	) {
		String provider = normalize(providerName);
		String instrument = normalizeInstId(instId);
		int size = Math.max(1, Math.min(5000, limit));
		if (provider != null && instrument != null) {
			return priceTickRepository.queryTicksByProviderAndInst(
				provider,
				instrument,
				fromTs,
				toTs,
				size
			).stream().map(this::toView).toList();
		}
		return priceTickRepository.queryTicks(
			provider,
			instrument,
			fromTs,
			toTs,
			PageRequest.of(0, size)
		).stream().map(this::toView).toList();
	}

	public List<PriceTickAggregateView> aggregate(
		String providerName,
		String instId,
		Long fromTs,
		Long toTs,
		long bucketMs,
		int limit
	) {
		long startNs = System.nanoTime();
		String provider = normalize(providerName);
		String instrument = normalizeInstId(instId);
		int bucketLimit = Math.max(1, Math.min(20000, limit));
		long bucketSize = Math.max(1000L, bucketMs);
		List<PriceTickRepository.PriceTickAggregateRow> rows;
		if (provider != null && instrument != null) {
			rows = priceTickRepository.queryTickAggregatesByProviderAndInst(
				provider,
				instrument,
				fromTs,
				toTs,
				bucketSize,
				bucketLimit
			);
		} else {
			rows = priceTickRepository.queryTickAggregates(
				provider,
				instrument,
				fromTs,
				toTs,
				bucketSize,
				bucketLimit
			);
		}
		List<PriceTickAggregateView> result = rows.stream().map(row -> new PriceTickAggregateView(
			row.getBucketStartTs(),
			row.getLastPrice(),
			row.getMinPrice(),
			row.getMaxPrice(),
			row.getCount()
		)).toList();
		long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
		if (latencyMs > AGGREGATE_SLOW_LOG_THRESHOLD_MS) {
			log.warn(
				"price.tick.aggregate.slow provider={} instId={} from={} to={} bucketMs={} rows={} latencyMs={} thresholdMs={}",
				provider,
				instrument,
				fromTs,
				toTs,
				bucketSize,
				result.size(),
				latencyMs,
				AGGREGATE_SLOW_LOG_THRESHOLD_MS
			);
		} else {
			log.info(
				"price.tick.aggregate.done provider={} instId={} from={} to={} bucketMs={} rows={} latencyMs={}",
				provider,
				instrument,
				fromTs,
				toTs,
				bucketSize,
				result.size(),
				latencyMs
			);
		}
		return result;
	}

	private PriceTickView toView(PriceTickEntity entity) {
		return new PriceTickView(
			entity.getId(),
			entity.getProviderName(),
			entity.getInstType(),
			entity.getInstId(),
			entity.getBaseSymbol(),
			entity.getQuoteSymbol(),
			entity.getPrice(),
			entity.getQuoteTs(),
			entity.getIngestedAt()
		);
	}

	private String normalize(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	private String normalizeInstId(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim().toUpperCase();
	}

	public record PriceTickView(
		Long id,
		String providerName,
		String instType,
		String instId,
		String baseSymbol,
		String quoteSymbol,
		BigDecimal price,
		Long quoteTs,
		Instant ingestedAt
	) {
	}

	public record PriceTickAggregateView(
		Long bucketStartTs,
		BigDecimal last,
		BigDecimal min,
		BigDecimal max,
		Long count
	) {
	}
}