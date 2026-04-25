package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.AddressHoldingQueryService;
import com.chainsentinel.core.service.dto.AddressTokenHoldingView;
import com.chainsentinel.infra.entity.AddressTokenHoldingEntity;
import com.chainsentinel.infra.repository.AddressTokenHoldingRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultAddressHoldingQueryService implements AddressHoldingQueryService {

	private static final Logger log = LoggerFactory.getLogger(DefaultAddressHoldingQueryService.class);

	private final AddressTokenHoldingRepository addressTokenHoldingRepository;
	private final AddressHoldingSnapshotService addressHoldingSnapshotService;
	private final SolanaBalanceWsSubscriptionService solanaBalanceWsSubscriptionService;

	public DefaultAddressHoldingQueryService(
		AddressTokenHoldingRepository addressTokenHoldingRepository,
		AddressHoldingSnapshotService addressHoldingSnapshotService,
		SolanaBalanceWsSubscriptionService solanaBalanceWsSubscriptionService
	) {
		this.addressTokenHoldingRepository = addressTokenHoldingRepository;
		this.addressHoldingSnapshotService = addressHoldingSnapshotService;
		this.solanaBalanceWsSubscriptionService = solanaBalanceWsSubscriptionService;
	}

	@Override
	@Transactional
	public List<AddressTokenHoldingView> list(String chain, String network, String address, int limit) {
		String normalizedChain = normalizeChain(chain);
		String normalizedNetwork = normalizeNetwork(network);
		String normalizedAddress = normalizeAddress(normalizedChain, address);
		int size = normalizeLimit(limit);
		if (shouldRefreshSolana(normalizedChain)) {
			solanaBalanceWsSubscriptionService.refreshSubscriptions();
		}
		AddressHoldingSnapshotService.SnapshotResult refreshResult = addressHoldingSnapshotService.refreshNativeHoldings(
			normalizedChain,
			normalizedNetwork,
			normalizedAddress
		);
		log.debug("holding.query.refresh chain={} network={} address={} scanned={} changed={} failed={}",
			normalizedChain, normalizedNetwork, normalizedAddress,
			refreshResult.scanned(), refreshResult.changed(), refreshResult.failed());
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

	private String normalizeAddress(String chain, String address) {
		if (!StringUtils.hasText(address)) {
			return null;
		}
		String trimmed = address.trim();
		if (isSolanaChain(chain)) {
			return trimmed;
		}
		String normalized = trimmed.toLowerCase(Locale.ROOT);
		if (looksLikeEvmAddress(normalized)) {
			if (!normalized.startsWith("0x")) {
				normalized = "0x" + normalized;
			}
			return normalized;
		}
		return normalized;
	}

	private boolean looksLikeEvmAddress(String address) {
		String value = address;
		if (value.startsWith("0x")) {
			value = value.substring(2);
		}
		if (value.length() != 40) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			boolean digit = c >= '0' && c <= '9';
			boolean hexLower = c >= 'a' && c <= 'f';
			if (!digit && !hexLower) {
				return false;
			}
		}
		return true;
	}

	private boolean isSolanaChain(String chain) {
		if (!StringUtils.hasText(chain)) {
			return false;
		}
		String normalized = chain.trim().toUpperCase(Locale.ROOT);
		return "SOL".equals(normalized) || "SOLANA".equals(normalized);
	}

	private boolean shouldRefreshSolana(String chain) {
		if (!StringUtils.hasText(chain)) {
			return true;
		}
		return isSolanaChain(chain);
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
