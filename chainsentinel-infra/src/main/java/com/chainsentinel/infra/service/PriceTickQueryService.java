package com.chainsentinel.infra.service;

import com.chainsentinel.infra.entity.PriceTickEntity;
import com.chainsentinel.infra.repository.PriceTickRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PriceTickQueryService {

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
		String provider = normalize(providerName);
		String instrument = normalizeInstId(instId);
		int size = Math.max(1, Math.min(20000, limit));
		long bucketSize = Math.max(1000L, bucketMs);

		List<PriceTickEntity> ticks = priceTickRepository.queryTicks(
			provider,
			instrument,
			fromTs,
			toTs,
			PageRequest.of(0, size)
		);
		Map<Long, AggregateAccumulator> buckets = new LinkedHashMap<>();
		for (PriceTickEntity tick : ticks) {
			if (tick == null || tick.getQuoteTs() == null || tick.getPrice() == null) {
				continue;
			}
			long bucketStart = (tick.getQuoteTs() / bucketSize) * bucketSize;
			AggregateAccumulator acc = buckets.get(bucketStart);
			if (acc == null) {
				buckets.put(bucketStart, new AggregateAccumulator(
					bucketStart,
					tick.getPrice(),
					tick.getPrice(),
					tick.getPrice(),
					1L
				));
			} else {
				acc.min = acc.min.min(tick.getPrice());
				acc.max = acc.max.max(tick.getPrice());
				acc.count++;
			}
		}

		List<PriceTickAggregateView> result = new ArrayList<>(buckets.size());
		for (AggregateAccumulator acc : buckets.values()) {
			result.add(new PriceTickAggregateView(
				acc.bucketStartTs,
				acc.last,
				acc.min,
				acc.max,
				acc.count
			));
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

	private static class AggregateAccumulator {
		private final Long bucketStartTs;
		private final BigDecimal last;
		private BigDecimal min;
		private BigDecimal max;
		private Long count;

		private AggregateAccumulator(Long bucketStartTs, BigDecimal last, BigDecimal min, BigDecimal max, Long count) {
			this.bucketStartTs = bucketStartTs;
			this.last = last;
			this.min = min;
			this.max = max;
			this.count = count;
		}
	}
}
