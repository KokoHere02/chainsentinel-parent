package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.MonitorAddressScopeService;
import com.chainsentinel.core.service.dto.MonitorAddressScopeUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorAddressScopeView;
import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import com.chainsentinel.infra.entity.MonitorScopeTokenEntity;
import com.chainsentinel.infra.repository.MonitorAddressScopeRepository;
import com.chainsentinel.infra.repository.MonitorScopeTokenRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultMonitorAddressScopeService implements MonitorAddressScopeService {

	private final MonitorAddressScopeRepository monitorAddressScopeRepository;
	private final MonitorScopeTokenRepository monitorScopeTokenRepository;

	public DefaultMonitorAddressScopeService(
		MonitorAddressScopeRepository monitorAddressScopeRepository,
		MonitorScopeTokenRepository monitorScopeTokenRepository
	) {
		this.monitorAddressScopeRepository = monitorAddressScopeRepository;
		this.monitorScopeTokenRepository = monitorScopeTokenRepository;
	}

	@Override
	@Transactional
	public MonitorAddressScopeView upsert(MonitorAddressScopeUpsertCommand command) {
		String chain = normalizeChain(command.chain());
		String network = normalizeNetwork(command.network());
		MonitorAddressScopeEntity entity = monitorAddressScopeRepository
			.findByMonitorAddressIdAndChainAndNetwork(command.monitorAddressId(), chain, network)
			.orElseGet(MonitorAddressScopeEntity::new);
		entity.setMonitorAddressId(command.monitorAddressId());
		entity.setChain(chain);
		entity.setNetwork(network);
		boolean enabled = Boolean.TRUE.equals(command.enabled());
		entity.setEnabled(enabled);
		MonitorAddressScopeEntity saved = monitorAddressScopeRepository.save(entity);
		if (!enabled) {
			disableChildren(saved.getId());
		}
		return toView(saved);
	}

	private void disableChildren(Long monitorScopeId) {
		List<MonitorScopeTokenEntity> tokens = monitorScopeTokenRepository.findByMonitorScopeId(monitorScopeId);
		boolean changed = false;
		for (MonitorScopeTokenEntity token : tokens) {
			if (Boolean.TRUE.equals(token.getEnabled())) {
				token.setEnabled(false);
				changed = true;
			}
		}
		if (changed) {
			monitorScopeTokenRepository.saveAll(tokens);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<MonitorAddressScopeView> list(Long monitorAddressId, String chain, String network, Boolean enabled, int limit) {
		String normalizedChain = normalizeChainOrNull(chain);
		String normalizedNetwork = normalizeNetworkOrNull(network);
		int size = normalizeLimit(limit);
		return monitorAddressScopeRepository.listByFilters(
			monitorAddressId,
			normalizedChain,
			normalizedNetwork,
			enabled,
			PageRequest.of(0, size)
		).stream().map(this::toView).toList();
	}

	private String normalizeChain(String chain) {
		return chain.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeChainOrNull(String chain) {
		if (!StringUtils.hasText(chain)) {
			return null;
		}
		return normalizeChain(chain);
	}

	private String normalizeNetwork(String network) {
		return network.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeNetworkOrNull(String network) {
		if (!StringUtils.hasText(network)) {
			return null;
		}
		return normalizeNetwork(network);
	}

	private int normalizeLimit(int limit) {
		return Math.max(1, Math.min(200, limit));
	}

	private MonitorAddressScopeView toView(MonitorAddressScopeEntity entity) {
		return new MonitorAddressScopeView(
			entity.getId(),
			entity.getMonitorAddressId(),
			entity.getChain(),
			entity.getNetwork(),
			entity.getEnabled()
		);
	}
}
