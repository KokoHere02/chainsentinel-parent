package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.MonitorScopeTokenService;
import com.chainsentinel.core.service.dto.MonitorScopeTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorScopeTokenView;
import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import com.chainsentinel.infra.entity.MonitorScopeTokenEntity;
import com.chainsentinel.infra.repository.MonitorAddressScopeRepository;
import com.chainsentinel.infra.repository.MonitorScopeTokenRepository;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultMonitorScopeTokenService implements MonitorScopeTokenService {

	private final MonitorScopeTokenRepository monitorScopeTokenRepository;
	private final MonitorAddressScopeRepository monitorAddressScopeRepository;

	public DefaultMonitorScopeTokenService(
		MonitorScopeTokenRepository monitorScopeTokenRepository,
		MonitorAddressScopeRepository monitorAddressScopeRepository
	) {
		this.monitorScopeTokenRepository = monitorScopeTokenRepository;
		this.monitorAddressScopeRepository = monitorAddressScopeRepository;
	}

	@Override
	@Transactional
	public MonitorScopeTokenView upsert(MonitorScopeTokenUpsertCommand command) {
		MonitorAddressScopeEntity scope = monitorAddressScopeRepository.findById(command.monitorScopeId())
			.orElseThrow(() -> new IllegalArgumentException("monitorScopeId not found: " + command.monitorScopeId()));
		validateTokenContract(command.tokenContract(), scope.getChain());
		String tokenContract = normalizeTokenContract(command.tokenContract(), scope.getChain());
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

	@Override
	@Transactional
	public void delete(Long id) {
		if (id == null || id <= 0) {
			throw new IllegalArgumentException("id must be positive");
		}
		if (!monitorScopeTokenRepository.existsById(id)) {
			throw new NoSuchElementException("scope token not found: " + id);
		}
		monitorScopeTokenRepository.deleteById(id);
	}

	private String normalizeTokenContract(String tokenContract, String chain) {
		String normalized = tokenContract.trim();
		if ("NATIVE".equalsIgnoreCase(normalized)) {
			return "NATIVE";
		}
		if (isSolanaChain(chain)) {
			return normalized;
		}
		return normalized.toLowerCase(Locale.ROOT);
	}

	private boolean isSolanaChain(String chain) {
		if (!StringUtils.hasText(chain)) {
			return false;
		}
		String normalized = chain.trim().toUpperCase(Locale.ROOT);
		return "SOL".equals(normalized) || "SOLANA".equals(normalized);
	}

	private void validateTokenContract(String tokenContract, String chain) {
		if (!StringUtils.hasText(tokenContract)) {
			throw new IllegalArgumentException("tokenContract is required");
		}
		String normalized = tokenContract.trim();
		if ("NATIVE".equalsIgnoreCase(normalized)) {
			return;
		}
		if (!isSolanaChain(chain)) {
			return;
		}
		validateSolanaMint(normalized);
	}

	private void validateSolanaMint(String mint) {
		if (mint.length() < 32 || mint.length() > 44) {
			throw new IllegalArgumentException("invalid solana mint length");
		}
		for (int i = 0; i < mint.length(); i++) {
			char c = mint.charAt(i);
			boolean digit = c >= '1' && c <= '9';
			boolean upper = c >= 'A' && c <= 'Z' && c != 'I' && c != 'O';
			boolean lower = c >= 'a' && c <= 'z' && c != 'l';
			if (!digit && !upper && !lower) {
				throw new IllegalArgumentException("invalid solana mint format");
			}
		}
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
