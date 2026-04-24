package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.MonitorTreeQueryService;
import com.chainsentinel.core.service.dto.MonitorAddressTreeView;
import com.chainsentinel.infra.entity.MonitorAddressEntity;
import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import com.chainsentinel.infra.entity.MonitorScopeTokenEntity;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import com.chainsentinel.infra.repository.MonitorAddressScopeRepository;
import com.chainsentinel.infra.repository.MonitorScopeTokenRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultMonitorTreeQueryService implements MonitorTreeQueryService {

	private final MonitorAddressRepository monitorAddressRepository;
	private final MonitorAddressScopeRepository monitorAddressScopeRepository;
	private final MonitorScopeTokenRepository monitorScopeTokenRepository;

	public DefaultMonitorTreeQueryService(
		MonitorAddressRepository monitorAddressRepository,
		MonitorAddressScopeRepository monitorAddressScopeRepository,
		MonitorScopeTokenRepository monitorScopeTokenRepository
	) {
		this.monitorAddressRepository = monitorAddressRepository;
		this.monitorAddressScopeRepository = monitorAddressScopeRepository;
		this.monitorScopeTokenRepository = monitorScopeTokenRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<MonitorAddressTreeView> tree(Boolean enabledOnly, int limit) {
		int size = Math.max(1, Math.min(500, limit));
		Boolean addressEnabled = Boolean.TRUE.equals(enabledOnly) ? Boolean.TRUE : null;
		List<MonitorAddressEntity> addresses = monitorAddressRepository.listByFilters(
			null,
			addressEnabled,
			PageRequest.of(0, size)
		);
		if (addresses.isEmpty()) {
			return Collections.emptyList();
		}

		List<Long> addressIds = addresses.stream().map(MonitorAddressEntity::getId).toList();
		List<MonitorAddressScopeEntity> scopes = monitorAddressScopeRepository
			.findByMonitorAddressIdInOrderByMonitorAddressIdAscIdAsc(addressIds);
		if (Boolean.TRUE.equals(enabledOnly)) {
			scopes = scopes.stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).toList();
		}

		Map<Long, List<MonitorAddressScopeEntity>> scopesByAddressId = scopes.stream()
			.collect(Collectors.groupingBy(MonitorAddressScopeEntity::getMonitorAddressId));
		List<Long> scopeIds = scopes.stream().map(MonitorAddressScopeEntity::getId).toList();

		List<MonitorScopeTokenEntity> tokens = scopeIds.isEmpty()
			? List.of()
			: monitorScopeTokenRepository.findByMonitorScopeIdInOrderByMonitorScopeIdAscIdAsc(scopeIds);
		if (Boolean.TRUE.equals(enabledOnly)) {
			tokens = tokens.stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).toList();
		}

		Map<Long, List<MonitorScopeTokenEntity>> tokensByScopeId = tokens.stream()
			.collect(Collectors.groupingBy(MonitorScopeTokenEntity::getMonitorScopeId));

		return addresses.stream()
			.map(address -> toAddressNode(address, scopesByAddressId, tokensByScopeId))
			.toList();
	}

	private MonitorAddressTreeView toAddressNode(
		MonitorAddressEntity address,
		Map<Long, List<MonitorAddressScopeEntity>> scopesByAddressId,
		Map<Long, List<MonitorScopeTokenEntity>> tokensByScopeId
	) {
		List<MonitorAddressTreeView.ScopeNode> scopeNodes = scopesByAddressId
			.getOrDefault(address.getId(), List.of())
			.stream()
			.map(scope -> toScopeNode(scope, tokensByScopeId.getOrDefault(scope.getId(), List.of())))
			.toList();
		return new MonitorAddressTreeView(
			address.getId(),
			address.getAddress(),
			address.getTag(),
			address.getEnabled(),
			scopeNodes
		);
	}

	private MonitorAddressTreeView.ScopeNode toScopeNode(
		MonitorAddressScopeEntity scope,
		List<MonitorScopeTokenEntity> tokens
	) {
		List<MonitorAddressTreeView.TokenNode> tokenNodes = tokens.stream()
			.map(this::toTokenNode)
			.toList();
		return new MonitorAddressTreeView.ScopeNode(
			scope.getId(),
			scope.getChain(),
			scope.getNetwork(),
			scope.getEnabled(),
			tokenNodes
		);
	}

	private MonitorAddressTreeView.TokenNode toTokenNode(MonitorScopeTokenEntity token) {
		return new MonitorAddressTreeView.TokenNode(
			token.getId(),
			token.getTokenContract(),
			token.getSymbol(),
			token.getDecimals(),
			token.getEnabled()
		);
	}
}
