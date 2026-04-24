package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.AddressHoldingQueryService;
import com.chainsentinel.core.service.dto.AddressTokenHoldingView;
import com.chainsentinel.infra.entity.AddressTokenHoldingEntity;
import com.chainsentinel.infra.repository.AddressTokenHoldingRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultAddressHoldingQueryService implements AddressHoldingQueryService {

	private final AddressTokenHoldingRepository addressTokenHoldingRepository;

	public DefaultAddressHoldingQueryService(AddressTokenHoldingRepository addressTokenHoldingRepository) {
		this.addressTokenHoldingRepository = addressTokenHoldingRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AddressTokenHoldingView> list(String chain, String network, String address, int limit) {
		String normalizedChain = normalizeChain(chain);
		String normalizedNetwork = normalizeNetwork(network);
		String normalizedAddress = normalizeAddress(address);
		int size = normalizeLimit(limit);
		return addressTokenHoldingRepository.listByFilters(
			normalizedChain,
			normalizedNetwork,
			normalizedAddress,
			PageRequest.of(0, size)
		).stream().map(this::toView).toList();
	}

	private String normalizeChain(String chain) {
		if (!StringUtils.hasText(chain)) {
			return null;
		}
		return chain.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeNetwork(String network) {
		if (!StringUtils.hasText(network)) {
			return null;
		}
		return network.trim();
	}

	private String normalizeAddress(String address) {
		if (!StringUtils.hasText(address)) {
			return null;
		}
		String normalized = address.trim().toLowerCase(Locale.ROOT);
		if (!normalized.startsWith("0x")) {
			normalized = "0x" + normalized;
		}
		return normalized;
	}

	private int normalizeLimit(int limit) {
		return Math.max(1, Math.min(200, limit));
	}

	private AddressTokenHoldingView toView(AddressTokenHoldingEntity entity) {
		return new AddressTokenHoldingView(
			entity.getId(),
			entity.getMonitorScopeId(),
			entity.getChain(),
			entity.getNetwork(),
			entity.getAddress(),
			entity.getTokenContract(),
			entity.getTokenSymbol(),
			entity.getDecimals(),
			entity.getBalanceRaw(),
			entity.getBalanceUpdatedAt()
		);
	}
}
