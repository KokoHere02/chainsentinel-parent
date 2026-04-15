package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.MonitorTokenService;
import com.chainsentinel.core.service.dto.MonitorTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorTokenView;
import com.chainsentinel.infra.entity.MonitorTokenEntity;
import com.chainsentinel.infra.repository.MonitorTokenRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultMonitorTokenService implements MonitorTokenService {

	private final MonitorTokenRepository monitorTokenRepository;

	public DefaultMonitorTokenService(MonitorTokenRepository monitorTokenRepository) {
		this.monitorTokenRepository = monitorTokenRepository;
	}

	@Override
	@Transactional
	public MonitorTokenView upsert(MonitorTokenUpsertCommand command) {
		String chain = command.chain().trim().toUpperCase();
		String tokenContract = command.tokenContract().trim().toLowerCase();

		MonitorTokenEntity entity = monitorTokenRepository.findByChainAndTokenContract(chain, tokenContract)
			.orElseGet(MonitorTokenEntity::new);

		entity.setChain(chain);
		entity.setTokenContract(tokenContract);
		entity.setSymbol(command.symbol());
		entity.setEnabled(Boolean.TRUE.equals(command.enabled()));

		MonitorTokenEntity saved = monitorTokenRepository.save(entity);
		return toView(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public List<MonitorTokenView> list(String chain, String keyword, Boolean enabled, int limit) {
		String normalizedChain = normalizeChain(chain);
		String normalizedKeyword = normalizeKeyword(keyword);
		int size = normalizeLimit(limit);
		return monitorTokenRepository.listByFilters(
			normalizedChain,
			normalizedKeyword,
			enabled,
			PageRequest.of(0, size)
		).stream().map(this::toView).toList();
	}

	private String normalizeChain(String chain) {
		if (!StringUtils.hasText(chain)) {
			return null;
		}
		return chain.trim().toUpperCase(Locale.ROOT);
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

	private MonitorTokenView toView(MonitorTokenEntity entity) {
		return new MonitorTokenView(
			entity.getId(),
			entity.getChain(),
			entity.getTokenContract(),
			entity.getSymbol(),
			entity.getEnabled()
		);
	}

}