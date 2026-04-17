package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.PricePullTargetService;
import com.chainsentinel.core.service.dto.PricePullTargetCreateCommand;
import com.chainsentinel.core.service.dto.PricePullTargetUpdateCommand;
import com.chainsentinel.core.service.dto.PricePullTargetView;
import com.chainsentinel.infra.entity.PriceProviderConfigEntity;
import com.chainsentinel.infra.entity.PricePullTargetEntity;
import com.chainsentinel.infra.repository.PriceProviderConfigRepository;
import com.chainsentinel.infra.repository.PricePullTargetRepository;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultPricePullTargetService implements PricePullTargetService {

	private final PricePullTargetRepository pricePullTargetRepository;
	private final PriceProviderConfigRepository priceProviderConfigRepository;
	private final PriceTickBackfillDispatchService backfillDispatchService;

	public DefaultPricePullTargetService(
		PricePullTargetRepository pricePullTargetRepository,
		PriceProviderConfigRepository priceProviderConfigRepository,
		PriceTickBackfillDispatchService backfillDispatchService
	) {
		this.pricePullTargetRepository = pricePullTargetRepository;
		this.priceProviderConfigRepository = priceProviderConfigRepository;
		this.backfillDispatchService = backfillDispatchService;
	}

	@Override
	@Transactional
	public PricePullTargetView create(PricePullTargetCreateCommand command) {
		PricePullTargetEntity entity = new PricePullTargetEntity();
		apply(entity, command.assetId(), command.providerConfigId(), command.instType(), command.instId(), command.quoteSymbol(),
			command.enabled(), command.pollIntervalMs(), command.priority());
		PricePullTargetEntity saved = saveTarget(entity);
		triggerBackfillIfNeeded(saved, "target_create");
		return toView(saved);
	}

	@Override
	@Transactional
	public PricePullTargetView update(Long id, PricePullTargetUpdateCommand command) {
		PricePullTargetEntity entity = pricePullTargetRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("price pull target not found: " + id));
		apply(entity, command.assetId(), command.providerConfigId(), command.instType(), command.instId(), command.quoteSymbol(),
			command.enabled(), command.pollIntervalMs(), command.priority());
		PricePullTargetEntity saved = saveTarget(entity);
		triggerBackfillIfNeeded(saved, "target_update");
		return toView(saved);
	}

	@Override
	@Transactional
	public void delete(Long id) {
		if (!pricePullTargetRepository.existsById(id)) {
			throw new NoSuchElementException("price pull target not found: " + id);
		}
		pricePullTargetRepository.deleteById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public PricePullTargetView get(Long id) {
		PricePullTargetEntity entity = pricePullTargetRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("price pull target not found: " + id));
		return toView(entity);
	}

	@Override
	@Transactional
	public PricePullTargetView setEnabled(Long id, boolean enabled) {
		PricePullTargetEntity entity = pricePullTargetRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("price pull target not found: " + id));
		entity.setEnabled(enabled);
		PricePullTargetEntity saved = pricePullTargetRepository.save(entity);
		triggerBackfillIfNeeded(saved, enabled ? "target_enable" : "target_disable");
		return toView(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public List<PricePullTargetView> list(Long providerConfigId, Boolean enabled, String keyword, int limit) {
		int size = Math.max(1, Math.min(500, limit));
		String normalizedKeyword = normalizeKeyword(keyword);
		return pricePullTargetRepository.listByFilters(providerConfigId, enabled, normalizedKeyword, PageRequest.of(0, size))
			.stream().map(this::toView).toList();
	}

	private PricePullTargetEntity saveTarget(PricePullTargetEntity entity) {
		try {
			return pricePullTargetRepository.save(entity);
		} catch (DataIntegrityViolationException ex) {
			throw new IllegalArgumentException(
				"price pull target already exists: providerConfigId=%d, instType=%s, instId=%s"
					.formatted(entity.getProviderConfigId(), entity.getInstType(), entity.getInstId())
			);
		}
	}

	private void triggerBackfillIfNeeded(PricePullTargetEntity entity, String trigger) {
		if (entity == null || !Boolean.TRUE.equals(entity.getEnabled())) {
			return;
		}
		priceProviderConfigRepository.findById(entity.getProviderConfigId()).ifPresent(config -> {
			if (isOkxProvider(config)) {
				backfillDispatchService.submitLast30Days(entity.getInstId(), trigger);
			}
		});
	}

	private boolean isOkxProvider(PriceProviderConfigEntity config) {
		return config != null
			&& StringUtils.hasText(config.getProviderName())
			&& "okx".equalsIgnoreCase(config.getProviderName().trim());
	}

	private void apply(
		PricePullTargetEntity entity,
		Long assetId,
		Long providerConfigId,
		String instType,
		String instId,
		String quoteSymbol,
		Boolean enabled,
		Integer pollIntervalMs,
		Integer priority
	) {
		if (assetId == null || assetId <= 0) {
			throw new IllegalArgumentException("assetId must be positive");
		}
		if (providerConfigId == null || providerConfigId <= 0) {
			throw new IllegalArgumentException("providerConfigId must be positive");
		}
		if (!priceProviderConfigRepository.existsById(providerConfigId)) {
			throw new IllegalArgumentException("providerConfigId not found: " + providerConfigId);
		}
		if (!StringUtils.hasText(instType)) {
			throw new IllegalArgumentException("instType is required");
		}
		if (!StringUtils.hasText(instId)) {
			throw new IllegalArgumentException("instId is required");
		}
		if (!StringUtils.hasText(quoteSymbol)) {
			throw new IllegalArgumentException("quoteSymbol is required");
		}
		if (priority == null || priority < 0) {
			throw new IllegalArgumentException("priority must be >= 0");
		}
		if (pollIntervalMs != null && pollIntervalMs <= 0) {
			throw new IllegalArgumentException("pollIntervalMs must be > 0 when set");
		}

		entity.setAssetId(assetId);
		entity.setProviderConfigId(providerConfigId);
		entity.setInstType(instType.trim().toUpperCase(Locale.ROOT));
		entity.setInstId(instId.trim().toUpperCase(Locale.ROOT));
		entity.setQuoteSymbol(quoteSymbol.trim().toUpperCase(Locale.ROOT));
		entity.setEnabled(enabled == null || enabled);
		entity.setPollIntervalMs(pollIntervalMs);
		entity.setPriority(priority);
	}

	private String normalizeKeyword(String keyword) {
		if (!StringUtils.hasText(keyword)) {
			return null;
		}
		return keyword.trim().toLowerCase(Locale.ROOT);
	}

	private PricePullTargetView toView(PricePullTargetEntity entity) {
		return new PricePullTargetView(
			entity.getId(),
			entity.getAssetId(),
			entity.getProviderConfigId(),
			entity.getInstType(),
			entity.getInstId(),
			entity.getQuoteSymbol(),
			entity.getEnabled(),
			entity.getPollIntervalMs(),
			entity.getPriority()
		);
	}
}