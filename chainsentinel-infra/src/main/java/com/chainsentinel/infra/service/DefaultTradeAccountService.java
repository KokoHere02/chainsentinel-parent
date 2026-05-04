package com.chainsentinel.infra.service;

import com.chainsentinel.common.crypto.AesGcmCryptoUtil;
import com.chainsentinel.core.service.TradeAccountService;
import com.chainsentinel.core.service.dto.TradeAccountConnectivityTestView;
import com.chainsentinel.core.service.dto.TradeAccountCreateCommand;
import com.chainsentinel.core.service.dto.TradeAccountStreamStatusView;
import com.chainsentinel.core.service.dto.TradeAccountUpdateCommand;
import com.chainsentinel.core.service.dto.TradeAccountView;
import com.chainsentinel.infra.entity.TradeAccountEntity;
import com.chainsentinel.infra.repository.TradeAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultTradeAccountService implements TradeAccountService {

	private final TradeAccountRepository tradeAccountRepository;
	private final AesGcmCryptoUtil aesGcmCryptoUtil;
	private final Map<String, TradeAccountConnectivityChecker> connectivityCheckerMap;
	private final TradeOrderStreamManager tradeOrderStreamManager;

	@Autowired
	public DefaultTradeAccountService(
		TradeAccountRepository tradeAccountRepository,
		AesGcmCryptoUtil aesGcmCryptoUtil,
		List<TradeAccountConnectivityChecker> connectivityCheckers
	) {
		this(tradeAccountRepository, aesGcmCryptoUtil, connectivityCheckers, new TradeOrderStreamManager() {
			@Override
			public void syncAccount(Long accountId) {
			}

			@Override
			public void disconnectAccount(Long accountId) {
			}

			@Override
			public TradeOrderStreamStatus status(Long accountId) {
				return null;
			}

			@Override
			public List<TradeOrderStreamStatus> statuses() {
				return List.of();
			}
		});
	}

	public DefaultTradeAccountService(
		TradeAccountRepository tradeAccountRepository,
		AesGcmCryptoUtil aesGcmCryptoUtil,
		List<TradeAccountConnectivityChecker> connectivityCheckers,
		TradeOrderStreamManager tradeOrderStreamManager
	) {
		this.tradeAccountRepository = tradeAccountRepository;
		this.aesGcmCryptoUtil = aesGcmCryptoUtil;
		this.tradeOrderStreamManager = tradeOrderStreamManager;
		this.connectivityCheckerMap = connectivityCheckers.stream()
			.collect(java.util.stream.Collectors.toMap(checker -> checker.provider().toUpperCase(Locale.ROOT), Function.identity()));
	}

	@Override
	@Transactional
	public TradeAccountView create(TradeAccountCreateCommand command, Long operatorUserId) {
		TradeAccountEntity entity = new TradeAccountEntity();
		applyForCreate(entity, command, operatorUserId);
		TradeAccountEntity saved = save(entity);
		tradeOrderStreamManager.syncAccount(saved.getId());
		return toView(saved);
	}

	@Override
	@Transactional
	public TradeAccountView update(Long id, TradeAccountUpdateCommand command, Long operatorUserId) {
		TradeAccountEntity entity = findRequired(id);
		applyForUpdate(entity, command, operatorUserId);
		TradeAccountEntity saved = save(entity);
		tradeOrderStreamManager.syncAccount(saved.getId());
		return toView(saved);
	}

	@Override
	@Transactional
	public void delete(Long id) {
		if (!tradeAccountRepository.existsById(id)) {
			throw new NoSuchElementException("trade account not found: " + id);
		}
		tradeAccountRepository.deleteById(id);
		tradeOrderStreamManager.disconnectAccount(id);
	}

	@Override
	@Transactional(readOnly = true)
	public TradeAccountView get(Long id) {
		return toView(findRequired(id));
	}

	@Override
	@Transactional(readOnly = true)
	public List<TradeAccountView> list(Boolean enabled, String provider, String keyword, int limit) {
		int size = Math.max(1, Math.min(500, limit));
		String normalizedProvider = StringUtils.hasText(provider) ? provider.trim().toLowerCase(Locale.ROOT) : null;
		String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
		return tradeAccountRepository.listByFilters(enabled, normalizedProvider, normalizedKeyword, PageRequest.of(0, size))
			.stream().map(this::toView).toList();
	}

	@Override
	@Transactional
	public TradeAccountView setEnabled(Long id, boolean enabled, Long operatorUserId) {
		TradeAccountEntity entity = findRequired(id);
		entity.setEnabled(enabled);
		entity.setUpdatedBy(operatorUserId);
		TradeAccountEntity saved = save(entity);
		if (enabled) {
			tradeOrderStreamManager.syncAccount(saved.getId());
		} else {
			tradeOrderStreamManager.disconnectAccount(saved.getId());
		}
		return toView(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public TradeAccountConnectivityTestView testConnectivity(Long id) {
		TradeAccountEntity entity = findRequired(id);
		TradeAccountConnectivityChecker checker = connectivityCheckerMap.get(entity.getProvider().toUpperCase(Locale.ROOT));
		if (checker == null) {
			return new TradeAccountConnectivityTestView(
				entity.getId(),
				entity.getProvider(),
				false,
				"unsupported provider for connectivity check: " + entity.getProvider(),
				Instant.now()
			);
		}
		String decryptedApiSecret = decryptIfPresent(entity.getApiSecretCipher());
		String decryptedPhrase = decryptIfPresent(entity.getPassphraseCipher());
		TradeConnectivityCheckResult result = checker.test(entity, decryptedApiSecret, decryptedPhrase);
		return new TradeAccountConnectivityTestView(
			entity.getId(),
			entity.getProvider(),
			result.success(),
			result.message(),
			Instant.now()
		);
	}

	@Override
	@Transactional(readOnly = true)
	public TradeAccountStreamStatusView streamStatus(Long id) {
		TradeAccountEntity entity = findRequired(id);
		TradeOrderStreamStatus status = tradeOrderStreamManager.status(id);
		if (status == null) {
			return new TradeAccountStreamStatusView(
				entity.getId(),
				entity.getProvider(),
				entity.getEnabled(),
				false,
				false,
				false,
				false,
				null,
				null,
				null,
				null,
				null
			);
		}
		return toStreamStatusView(status);
	}

	@Override
	@Transactional(readOnly = true)
	public List<TradeAccountStreamStatusView> streamStatuses() {
		return tradeOrderStreamManager.statuses().stream()
			.map(this::toStreamStatusView)
			.toList();
	}

	private TradeAccountEntity findRequired(Long id) {
		return tradeAccountRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("trade account not found: " + id));
	}

	private TradeAccountEntity save(TradeAccountEntity entity) {
		try {
			return tradeAccountRepository.save(entity);
		} catch (DataIntegrityViolationException ex) {
			throw new IllegalArgumentException(
				"trade account already exists: provider=%s, name=%s".formatted(entity.getProvider(), entity.getName())
			);
		}
	}

	private void applyForCreate(TradeAccountEntity entity, TradeAccountCreateCommand command, Long operatorUserId) {
		String accountType = normalizeAccountType(command.accountType());
		String envType = normalizeEnvType(command.envType());
		String provider = normalizeProvider(command.provider());
		String name = normalizeRequired(command.name(), "name");
		entity.setName(name);
		entity.setProvider(provider);
		entity.setAccountType(accountType);
		entity.setEnvType(envType);
		entity.setApiKey(normalizeNullable(command.apiKey()));
		entity.setEnabled(command.enabled() == null || command.enabled());
		entity.setRemark(normalizeNullable(command.remark()));
		entity.setCreatedBy(operatorUserId);
		entity.setUpdatedBy(operatorUserId);
		applySecrets(entity, command.apiSecret(), command.passphrase(), true);
		validateAccount(entity);
	}

	private void applyForUpdate(TradeAccountEntity entity, TradeAccountUpdateCommand command, Long operatorUserId) {
		entity.setName(normalizeRequired(command.name(), "name"));
		entity.setProvider(normalizeProvider(command.provider()));
		entity.setAccountType(normalizeAccountType(command.accountType()));
		entity.setEnvType(normalizeEnvType(command.envType()));
		entity.setEnabled(command.enabled() == null || command.enabled());
		entity.setRemark(normalizeNullable(command.remark()));
		entity.setUpdatedBy(operatorUserId);
		if (command.apiKey() != null) {
			entity.setApiKey(normalizeNullable(command.apiKey()));
		}
		applySecrets(entity, command.apiSecret(), command.passphrase(), false);
		validateAccount(entity);
	}

	private void applySecrets(TradeAccountEntity entity, String apiSecret, String passphrase, boolean createMode) {
		if (apiSecret != null) {
			entity.setApiSecretCipher(StringUtils.hasText(apiSecret) ? aesGcmCryptoUtil.encrypt(apiSecret.trim()) : null);
		} else if (createMode) {
			entity.setApiSecretCipher(null);
		}
		if (passphrase != null) {
			entity.setPassphraseCipher(StringUtils.hasText(passphrase) ? aesGcmCryptoUtil.encrypt(passphrase.trim()) : null);
		} else if (createMode) {
			entity.setPassphraseCipher(null);
		}
	}

	private void validateAccount(TradeAccountEntity entity) {
		if (!"API_KEY".equals(entity.getAccountType())) {
			throw new IllegalArgumentException("unsupported accountType: " + entity.getAccountType());
		}
		if ("OKX".equals(entity.getProvider())) {
			if (!StringUtils.hasText(entity.getApiKey())) {
				throw new IllegalArgumentException("apiKey is required for OKX account");
			}
			if (!StringUtils.hasText(entity.getApiSecretCipher())) {
				throw new IllegalArgumentException("apiSecret is required for OKX account");
			}
			if (!StringUtils.hasText(entity.getPassphraseCipher())) {
				throw new IllegalArgumentException("passphrase is required for OKX account");
			}
		}
	}

	private String normalizeRequired(String value, String fieldName) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		return value.trim();
	}

	private String normalizeNullable(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	private String normalizeProvider(String provider) {
		return normalizeRequired(provider, "provider").toUpperCase(Locale.ROOT);
	}

	private String normalizeAccountType(String accountType) {
		String normalized = StringUtils.hasText(accountType) ? accountType.trim().toUpperCase(Locale.ROOT) : "API_KEY";
		if (!"API_KEY".equals(normalized) && !"WALLET".equals(normalized)) {
			throw new IllegalArgumentException("unsupported accountType: " + normalized);
		}
		return normalized;
	}

	private String normalizeEnvType(String envType) {
		String normalized = StringUtils.hasText(envType) ? envType.trim().toUpperCase(Locale.ROOT) : "SIMULATED";
		if (!"SIMULATED".equals(normalized) && !"LIVE".equals(normalized)) {
			throw new IllegalArgumentException("unsupported envType: " + normalized);
		}
		return normalized;
	}

	private String decryptIfPresent(String cipherText) {
		if (!StringUtils.hasText(cipherText)) {
			return null;
		}
		return aesGcmCryptoUtil.decrypt(cipherText);
	}

	private TradeAccountView toView(TradeAccountEntity entity) {
		return new TradeAccountView(
			entity.getId(),
			entity.getName(),
			entity.getProvider(),
			entity.getAccountType(),
			entity.getEnvType(),
			maskApiKey(entity.getApiKey()),
			StringUtils.hasText(entity.getApiSecretCipher()),
			StringUtils.hasText(entity.getPassphraseCipher()),
			entity.getEnabled(),
			entity.getRemark(),
			entity.getCreatedBy(),
			entity.getUpdatedBy()
		);
	}

	private TradeAccountStreamStatusView toStreamStatusView(TradeOrderStreamStatus status) {
		return new TradeAccountStreamStatusView(
			status.accountId(),
			status.provider(),
			status.enabled(),
			status.connected(),
			status.loggedIn(),
			status.orderSubscribed(),
			status.assetSubscribed(),
			status.lastMessageAt(),
			status.lastOrderMessageAt(),
			status.lastAssetMessageAt(),
			status.lastErrorType(),
			status.lastErrorMessage()
		);
	}

	private String maskApiKey(String apiKey) {
		if (!StringUtils.hasText(apiKey)) {
			return null;
		}
		String trimmed = apiKey.trim();
		if (trimmed.length() <= 8) {
			return "****";
		}
		return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
	}
}
