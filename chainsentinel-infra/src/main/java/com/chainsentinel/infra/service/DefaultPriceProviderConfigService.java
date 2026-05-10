package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.PriceProviderConfigService;
import com.chainsentinel.core.service.dto.PriceProviderConfigCreateCommand;
import com.chainsentinel.core.service.dto.PriceProviderConfigUpdateCommand;
import com.chainsentinel.core.service.dto.PriceProviderConfigView;
import com.chainsentinel.infra.entity.PriceProviderConfigEntity;
import com.chainsentinel.infra.repository.PriceProviderConfigRepository;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultPriceProviderConfigService implements PriceProviderConfigService {

	private final PriceProviderConfigRepository priceProviderConfigRepository;
	private final PriceProviderRuntimeConfig priceProviderRuntimeConfig;

	public DefaultPriceProviderConfigService(
		PriceProviderConfigRepository priceProviderConfigRepository,
		PriceProviderRuntimeConfig priceProviderRuntimeConfig
	) {
		this.priceProviderConfigRepository = priceProviderConfigRepository;
		this.priceProviderRuntimeConfig = priceProviderRuntimeConfig;
	}

	@Override
	@Transactional
	public PriceProviderConfigView create(PriceProviderConfigCreateCommand command) {
		PriceProviderConfigEntity entity = new PriceProviderConfigEntity();
		apply(entity, command.providerName(), command.baseUrl(), command.enabled(), command.priority(), command.timeoutMs());
		PriceProviderConfigEntity saved = save(entity);
		refreshRuntimeCache();
		return toView(saved);
	}

	@Override
	@Transactional
	public PriceProviderConfigView update(Long id, PriceProviderConfigUpdateCommand command) {
		PriceProviderConfigEntity entity = priceProviderConfigRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("price provider config not found: " + id));
		apply(entity, command.providerName(), command.baseUrl(), command.enabled(), command.priority(), command.timeoutMs());
		PriceProviderConfigEntity saved = save(entity);
		refreshRuntimeCache();
		return toView(saved);
	}

	@Override
	@Transactional
	public void delete(Long id) {
		if (!priceProviderConfigRepository.existsById(id)) {
			throw new NoSuchElementException("price provider config not found: " + id);
		}
		try {
			priceProviderConfigRepository.deleteById(id);
		} catch (DataIntegrityViolationException ex) {
			throw new IllegalArgumentException("price provider config is in use by pull targets: " + id);
		}
		refreshRuntimeCache();
	}

	@Override
	@Transactional(readOnly = true)
	public PriceProviderConfigView get(Long id) {
		PriceProviderConfigEntity entity = priceProviderConfigRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("price provider config not found: " + id));
		return toView(entity);
	}

	@Override
	@Transactional(readOnly = true)
	public List<PriceProviderConfigView> list(Boolean enabled, String keyword, int limit) {
		int size = Math.max(1, Math.min(500, limit));
		String normalizedKeyword = normalizeKeyword(keyword);
		return priceProviderConfigRepository.listByFilters(enabled, normalizedKeyword, PageRequest.of(0, size))
			.stream().map(this::toView).toList();
	}

	@Override
	@Transactional
	public PriceProviderConfigView setEnabled(Long id, boolean enabled) {
		PriceProviderConfigEntity entity = priceProviderConfigRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("price provider config not found: " + id));
		entity.setEnabled(enabled);
		PriceProviderConfigEntity saved = priceProviderConfigRepository.save(entity);
		refreshRuntimeCache();
		return toView(saved);
	}

	private PriceProviderConfigEntity save(PriceProviderConfigEntity entity) {
		try {
			return priceProviderConfigRepository.save(entity);
		} catch (DataIntegrityViolationException ex) {
			throw new IllegalArgumentException("price provider config already exists: providerName=" + entity.getProviderName());
		}
	}

	private void apply(
		PriceProviderConfigEntity entity,
		String providerName,
		String baseUrl,
		Boolean enabled,
		Integer priority,
		Integer timeoutMs
	) {
		if (!StringUtils.hasText(providerName)) {
			throw new IllegalArgumentException("providerName is required");
		}
		if (!StringUtils.hasText(baseUrl)) {
			throw new IllegalArgumentException("baseUrl is required");
		}
		if (priority == null || priority < 0) {
			throw new IllegalArgumentException("priority must be >= 0");
		}
		if (timeoutMs == null || timeoutMs <= 0) {
			throw new IllegalArgumentException("timeoutMs must be > 0");
		}
		entity.setProviderName(providerName.trim().toLowerCase(Locale.ROOT));
		entity.setBaseUrl(UrlSchemeSupport.requireSupported(baseUrl, "baseUrl"));
		entity.setEnabled(enabled == null || enabled);
		entity.setPriority(priority);
		entity.setTimeoutMs(timeoutMs);
	}

	private String normalizeKeyword(String keyword) {
		if (!StringUtils.hasText(keyword)) {
			return null;
		}
		return keyword.trim().toLowerCase(Locale.ROOT);
	}

	private void refreshRuntimeCache() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					priceProviderRuntimeConfig.refreshCache();
				}
			});
			return;
		}
		priceProviderRuntimeConfig.refreshCache();
	}

	private PriceProviderConfigView toView(PriceProviderConfigEntity entity) {
		return new PriceProviderConfigView(
			entity.getId(),
			entity.getProviderName(),
			entity.getBaseUrl(),
			entity.getEnabled(),
			entity.getPriority(),
			entity.getTimeoutMs()
		);
	}
}
