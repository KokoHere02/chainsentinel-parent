package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.MonitorAddressService;
import com.chainsentinel.core.service.dto.MonitorAddressUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorAddressView;
import com.chainsentinel.infra.entity.MonitorAddressEntity;
import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import com.chainsentinel.infra.entity.MonitorScopeTokenEntity;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import com.chainsentinel.infra.repository.MonitorAddressScopeRepository;
import com.chainsentinel.infra.repository.MonitorScopeTokenRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultMonitorAddressService implements MonitorAddressService {

	private final MonitorAddressRepository monitorAddressRepository;
	private final MonitorAddressScopeRepository monitorAddressScopeRepository;
	private final MonitorScopeTokenRepository monitorScopeTokenRepository;

	public DefaultMonitorAddressService(
		MonitorAddressRepository monitorAddressRepository,
		MonitorAddressScopeRepository monitorAddressScopeRepository,
		MonitorScopeTokenRepository monitorScopeTokenRepository
	) {
		this.monitorAddressRepository = monitorAddressRepository;
		this.monitorAddressScopeRepository = monitorAddressScopeRepository;
		this.monitorScopeTokenRepository = monitorScopeTokenRepository;
	}

	@Override
	@Transactional
	public MonitorAddressView upsert(MonitorAddressUpsertCommand command) {
		String address = command.address().trim().toLowerCase();

		MonitorAddressEntity entity = monitorAddressRepository.findByAddress(address)
			.orElseGet(MonitorAddressEntity::new);

		entity.setAddress(address);
		entity.setTag(command.tag());
		boolean enabled = Boolean.TRUE.equals(command.enabled());
		entity.setEnabled(enabled);

		MonitorAddressEntity saved = monitorAddressRepository.save(entity);
		if (!enabled) {
			disableChildren(saved.getId());
		}
		return toView(saved);
	}

	private void disableChildren(Long monitorAddressId) {
		List<MonitorAddressScopeEntity> scopes = monitorAddressScopeRepository.findByMonitorAddressId(monitorAddressId);
		if (scopes.isEmpty()) {
			return;
		}
		boolean changedScopes = false;
		for (MonitorAddressScopeEntity scope : scopes) {
			if (Boolean.TRUE.equals(scope.getEnabled())) {
				scope.setEnabled(false);
				changedScopes = true;
			}
		}
		if (changedScopes) {
			monitorAddressScopeRepository.saveAll(scopes);
		}

		List<Long> scopeIds = scopes.stream().map(MonitorAddressScopeEntity::getId).toList();
		List<MonitorScopeTokenEntity> tokens = monitorScopeTokenRepository.findByMonitorScopeIdInOrderByMonitorScopeIdAscIdAsc(
			scopeIds);
		boolean changedTokens = false;
		for (MonitorScopeTokenEntity token : tokens) {
			if (Boolean.TRUE.equals(token.getEnabled())) {
				token.setEnabled(false);
				changedTokens = true;
			}
		}
		if (changedTokens) {
			monitorScopeTokenRepository.saveAll(tokens);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<MonitorAddressView> list(String keyword, Boolean enabled, int limit) {
		String normalizedKeyword = normalizeKeyword(keyword);
		int size = normalizeLimit(limit);
		return monitorAddressRepository.listByFilters(
			normalizedKeyword,
			enabled,
			PageRequest.of(0, size)
		).stream().map(this::toView).toList();
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

	private MonitorAddressView toView(MonitorAddressEntity entity) {
		return new MonitorAddressView(
			entity.getId(),
			entity.getAddress(),
			entity.getTag(),
			entity.getEnabled()
		);
	}

}
