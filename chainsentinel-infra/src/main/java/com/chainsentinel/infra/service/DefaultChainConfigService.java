package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.ChainConfigService;
import com.chainsentinel.core.service.dto.ChainConfigUpsertCommand;
import com.chainsentinel.core.service.dto.ChainConfigView;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultChainConfigService implements ChainConfigService {

	private final ChainConfigRepository chainConfigRepository;
	private final ChainConfigRpcUrlCodec chainConfigRpcUrlCodec;

	public DefaultChainConfigService(
		ChainConfigRepository chainConfigRepository,
		ChainConfigRpcUrlCodec chainConfigRpcUrlCodec
	) {
		this.chainConfigRepository = chainConfigRepository;
		this.chainConfigRpcUrlCodec = chainConfigRpcUrlCodec;
	}

	@Override
	@Transactional
	public ChainConfigView upsert(ChainConfigUpsertCommand command) {
		String rpcHttpUrl = resolveHttpRpcUrl(command);
		String rpcWsUrl = resolveWsRpcUrl(command.rpcWsUrl());
		String balanceProtocol = normalizeBalanceProtocol(command.balanceProtocol());
		ChainConfigEntity entity = chainConfigRepository
			.findByChainAndNetwork(command.chain(), command.network())
			.orElseGet(ChainConfigEntity::new);

		String encryptedRpcHttpUrl = chainConfigRpcUrlCodec.encrypt(rpcHttpUrl);
		String encryptedRpcWsUrl = StringUtils.hasText(rpcWsUrl) ? chainConfigRpcUrlCodec.encrypt(rpcWsUrl) : null;

		entity.setChain(command.chain());
		entity.setNetwork(command.network());
		entity.setRpcHttpUrl(encryptedRpcHttpUrl);
		entity.setRpcWsUrl(encryptedRpcWsUrl);
		entity.setActiveProtocol(balanceProtocol);
		// Keep legacy column for compatibility with old logic and rollback safety.
		entity.setRpcUrl(encryptedRpcHttpUrl);
		entity.setConfirmRequired(command.confirmRequired());
		entity.setEnabled(command.enabled());

		ChainConfigEntity saved = chainConfigRepository.save(entity);
		return toView(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ChainConfigView> list() {
		return chainConfigRepository.findAllByOrderByChainAscNetworkAsc().stream()
			.map(this::toView)
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ChainConfigView> find(String chain, String network) {
		return chainConfigRepository.findByChainAndNetwork(chain, network)
			.map(this::toView);
	}

	@Override
	@Transactional
	public boolean delete(String chain, String network) {
		Optional<ChainConfigEntity> existed = chainConfigRepository.findByChainAndNetwork(chain, network);
		if (existed.isEmpty()) {
			return false;
		}
		chainConfigRepository.delete(existed.get());
		return true;
	}

	@Override
	@Transactional
	public Optional<ChainConfigView> setEnabled(String chain, String network, boolean enabled) {
		Optional<ChainConfigEntity> existed = chainConfigRepository.findByChainAndNetwork(chain, network);
		if (existed.isEmpty()) {
			return Optional.empty();
		}

		ChainConfigEntity entity = existed.get();
		entity.setEnabled(enabled);
		ChainConfigEntity saved = chainConfigRepository.save(entity);
		return Optional.of(toView(saved));
	}

	private ChainConfigView toView(ChainConfigEntity entity) {
		String rpcHttpUrl = decrypt(entity.getRpcHttpUrl(), entity.getChain(), entity.getNetwork());
		String rpcWsUrl = decrypt(entity.getRpcWsUrl(), entity.getChain(), entity.getNetwork());
		String rpcUrl = rpcHttpUrl;
		if (!StringUtils.hasText(rpcUrl)) {
			rpcUrl = decrypt(entity.getRpcUrl(), entity.getChain(), entity.getNetwork());
		}
		return new ChainConfigView(
			entity.getId(),
			entity.getChain(),
			entity.getNetwork(),
			rpcUrl,
			rpcHttpUrl,
			rpcWsUrl,
			normalizeBalanceProtocol(entity.getActiveProtocol()),
			entity.getConfirmRequired(),
			entity.getEnabled()
		);
	}

	private String decrypt(String value, String chain, String network) {
		String decrypted = chainConfigRpcUrlCodec.decryptIfNeeded(value, chain, network);
		return decrypted == null ? value : decrypted;
	}

	private String resolveHttpRpcUrl(ChainConfigUpsertCommand command) {
		String candidate = StringUtils.hasText(command.rpcHttpUrl()) ? command.rpcHttpUrl() : command.rpcUrl();
		if (!StringUtils.hasText(candidate)) {
			throw new IllegalArgumentException("rpcHttpUrl is required");
		}
		String validated = UrlSchemeSupport.requireSupported(candidate, "rpcHttpUrl");
		String scheme = UrlSchemeSupport.schemeOf(validated);
		if (!"http".equals(scheme) && !"https".equals(scheme)) {
			throw new IllegalArgumentException("rpcHttpUrl must use http/https");
		}
		return validated;
	}

	private String resolveWsRpcUrl(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		String validated = UrlSchemeSupport.requireSupported(raw, "rpcWsUrl");
		String scheme = UrlSchemeSupport.schemeOf(validated);
		if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
			throw new IllegalArgumentException("rpcWsUrl must use ws/wss");
		}
		return validated;
	}

	private String normalizeBalanceProtocol(String value) {
		if (!StringUtils.hasText(value)) {
			return "HTTP";
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		if (!"HTTP".equals(normalized) && !"WS".equals(normalized)) {
			throw new IllegalArgumentException("balanceProtocol must be HTTP or WS");
		}
		return normalized;
	}

}
