package com.chainsentinel.infra.service;

import com.chainsentinel.infra.entity.PriceTickEntity;
import com.chainsentinel.infra.repository.PriceTickRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
}

