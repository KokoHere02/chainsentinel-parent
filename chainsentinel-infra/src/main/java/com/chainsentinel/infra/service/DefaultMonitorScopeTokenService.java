package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.MonitorScopeTokenService;
import com.chainsentinel.core.service.dto.MonitorScopeTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorScopeTokenView;
import com.chainsentinel.infra.entity.MonitorScopeTokenEntity;
import com.chainsentinel.infra.repository.MonitorScopeTokenRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultMonitorScopeTokenService implements MonitorScopeTokenService {

	private final MonitorScopeTokenRepository monitorScopeTokenRepository;

	public DefaultMonitorScopeTokenService(MonitorScopeTokenRepository monitorScopeTokenRepository) {
		this.monitorScopeTokenRepository = monitorScopeTokenRepository;
	}

	@Override
	@Transactional
	public MonitorScopeTokenView upsert(MonitorScopeTokenUpsertCommand command) {
		String tokenContract = normalizeTokenContract(command.tokenContract());
		MonitorScopeTokenEntity entity = monitorScopeTokenRepository
			.findByMonitorScopeIdAndTokenContract(command.monitorScopeId(), tokenContract)
			.orElseGet(MonitorScopeTokenEntity::new);
		entity.setMonitorScopeId(command.monitorScopeId());
		entity.setTokenContract(tokenContract);
		entity.setSymbol(command.symbol());
		entity.setDecimals(command.decimals());
		entity.setEnabled(Boolean.TRUE.equals(command.enabled()));
		MonitorScopeTokenEntity saved = monitorScopeTokenRepository.save(entity);
		return toView(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public List<MonitorScopeTokenView> list(Long monitorScopeId, String keyword, Boolean enabled, int limit) {
		String normalizedKeyword = normalizeKeyword(keyword);
		int size = normalizeLimit(limit);
		return monitorScopeTokenRepository.listByFilters(
			monitorScopeId,
			normalizedKeyword,
			enabled,
			PageRequest.of(0, size)
		).stream().map(this::toView).toList();
	}

	private String normalizeTokenContract(String tokenContract) {
		String normalized = tokenContract.trim();
		if ("NATIVE".equalsIgnoreCase(normalized)) {
			return "NATIVE";
		}
		return normalized.toLowerCase(Locale.ROOT);
	}

	private String normalizeKeyword(String keyword) {
		if (!StringUtils.hasText(keyword)) {
			return null;
		}
		return keyword.trim().toLowerCase(Locale.ROOT);
	}

	private int normalizeLimit(int limit) {
		return Math.max(1, Math.min(200, limit));
	}

	private MonitorScopeTokenView toView(MonitorScopeTokenEntity entity) {
		return new MonitorScopeTokenView(
			entity.getId(),
			entity.getMonitorScopeId(),
			entity.getTokenContract(),
			entity.getSymbol(),
			entity.getDecimals(),
			entity.getEnabled()
		);
	}
}

