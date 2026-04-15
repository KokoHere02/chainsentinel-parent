package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.MonitorAddressService;
import com.chainsentinel.core.service.dto.MonitorAddressUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorAddressView;
import com.chainsentinel.infra.entity.MonitorAddressEntity;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultMonitorAddressService implements MonitorAddressService {

	private final MonitorAddressRepository monitorAddressRepository;

	public DefaultMonitorAddressService(MonitorAddressRepository monitorAddressRepository) {
		this.monitorAddressRepository = monitorAddressRepository;
	}

	@Override
	@Transactional
	public MonitorAddressView upsert(MonitorAddressUpsertCommand command) {
		String chain = command.chain().trim().toUpperCase();
		String address = command.address().trim().toLowerCase();

		MonitorAddressEntity entity = monitorAddressRepository.findByChainAndAddress(chain, address)
			.orElseGet(MonitorAddressEntity::new);

		entity.setChain(chain);
		entity.setAddress(address);
		entity.setTag(command.tag());
		entity.setEnabled(Boolean.TRUE.equals(command.enabled()));

		MonitorAddressEntity saved = monitorAddressRepository.save(entity);
		return toView(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public List<MonitorAddressView> search(String chain, String keyword, int limit, boolean enabledOnly) {
		String normalizedChain = normalizeChain(chain);
		String normalizedKeyword = normalizeKeyword(keyword);
		int size = normalizeLimit(limit);
		return monitorAddressRepository.search(
			normalizedChain,
			normalizedKeyword,
			enabledOnly,
			PageRequest.of(0, size)
		).stream().map(this::toView).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<MonitorAddressView> list(String chain, String keyword, Boolean enabled, int limit) {
		String normalizedChain = normalizeChain(chain);
		String normalizedKeyword = normalizeKeyword(keyword);
		int size = normalizeLimit(limit);
		return monitorAddressRepository.listByFilters(
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

	private MonitorAddressView toView(MonitorAddressEntity entity) {
		return new MonitorAddressView(
			entity.getId(),
			entity.getChain(),
			entity.getAddress(),
			entity.getTag(),
			entity.getEnabled()
		);
	}

}