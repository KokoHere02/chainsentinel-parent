package com.chainsentinel.infra.service;

import com.chainsentinel.common.crypto.AesGcmCryptoUtil;
import com.chainsentinel.core.service.TradeOrderService;
import com.chainsentinel.core.service.dto.TradeFillView;
import com.chainsentinel.core.service.dto.TradeOrderCancelView;
import com.chainsentinel.core.service.dto.TradeOrderCreateCommand;
import com.chainsentinel.core.service.dto.TradeOrderQuery;
import com.chainsentinel.core.service.dto.TradeOrderView;
import com.chainsentinel.infra.entity.TradeAccountEntity;
import com.chainsentinel.infra.entity.TradeFillEntity;
import com.chainsentinel.infra.entity.TradeOrderEntity;
import com.chainsentinel.infra.repository.TradeAccountRepository;
import com.chainsentinel.infra.repository.TradeFillRepository;
import com.chainsentinel.infra.repository.TradeOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultTradeOrderService implements TradeOrderService {

	private final TradeOrderRepository tradeOrderRepository;
	private final TradeAccountRepository tradeAccountRepository;
	private final TradeFillRepository tradeFillRepository;
	private final AesGcmCryptoUtil aesGcmCryptoUtil;
	private final Map<String, TradeOrderProvider> providerMap;

	public DefaultTradeOrderService(
		TradeOrderRepository tradeOrderRepository,
		TradeAccountRepository tradeAccountRepository,
		TradeFillRepository tradeFillRepository,
		AesGcmCryptoUtil aesGcmCryptoUtil,
		List<TradeOrderProvider> providers
	) {
		this.tradeOrderRepository = tradeOrderRepository;
		this.tradeAccountRepository = tradeAccountRepository;
		this.tradeFillRepository = tradeFillRepository;
		this.aesGcmCryptoUtil = aesGcmCryptoUtil;
		this.providerMap = providers.stream()
			.collect(java.util.stream.Collectors.toMap(provider -> provider.provider().toUpperCase(Locale.ROOT), Function.identity()));
	}

	@Override
	@Transactional
	public TradeOrderView create(TradeOrderCreateCommand command, Long operatorUserId) {
		validateCreateCommand(command);
		TradeAccountEntity account = tradeAccountRepository.findById(command.accountId())
			.orElseThrow(() -> new NoSuchElementException("trade account not found: " + command.accountId()));
		if (!Boolean.TRUE.equals(account.getEnabled())) {
			throw new IllegalArgumentException("trade account is disabled: " + account.getId());
		}
		TradeOrderProvider provider = requireProvider(account.getProvider());
		TradeOrderEntity entity = new TradeOrderEntity();
		entity.setAccountId(account.getId());
		entity.setClientOrderId(normalizeClientOrderId(command.clientOrderId()));
		entity.setProvider(account.getProvider());
		entity.setMarketType("SPOT");
		entity.setSymbol(command.symbol().trim().toUpperCase(Locale.ROOT));
		entity.setSide(command.side().trim().toUpperCase(Locale.ROOT));
		entity.setOrderType(command.orderType().trim().toUpperCase(Locale.ROOT));
		entity.setPrice(command.price());
		entity.setQuantity(command.quantity());
		entity.setQuoteAmount(command.quoteAmount());
		entity.setStatus("PENDING_SUBMIT");
		entity.setFilledQuantity(BigDecimal.ZERO);
		entity.setFilledAmount(BigDecimal.ZERO);
		entity.setCreatedBy(operatorUserId);
		TradeOrderEntity saved = save(entity);
		TradeProviderSubmitResult result = provider.submit(
			account,
			decryptRequired(account.getApiSecretCipher(), "apiSecret"),
			decryptRequired(account.getPassphraseCipher(), "passphrase"),
			new TradeOrderCreateCommand(
				command.accountId(),
				entity.getSymbol(),
				entity.getSide(),
				entity.getOrderType(),
				entity.getPrice(),
				entity.getQuantity(),
				entity.getQuoteAmount(),
				entity.getClientOrderId()
			)
		);
		saved.setProviderOrderId(result.providerOrderId());
		saved.setStatus(result.status());
		saved.setErrorCode(result.errorCode());
		saved.setErrorMessage(result.errorMessage());
		return toView(tradeOrderRepository.save(saved));
	}

	@Override
	@Transactional
	public TradeOrderCancelView cancel(Long orderId, Long operatorUserId) {
		TradeOrderEntity order = tradeOrderRepository.findById(orderId)
			.orElseThrow(() -> new NoSuchElementException("trade order not found: " + orderId));
		if (!"SUBMITTED".equals(order.getStatus()) && !"PARTIALLY_FILLED".equals(order.getStatus())) {
			throw new IllegalArgumentException("order status does not allow cancel: " + order.getStatus());
		}
		TradeAccountEntity account = tradeAccountRepository.findById(order.getAccountId())
			.orElseThrow(() -> new NoSuchElementException("trade account not found: " + order.getAccountId()));
		TradeOrderProvider provider = requireProvider(account.getProvider());
		TradeProviderCancelResult result = provider.cancel(
			account,
			decryptRequired(account.getApiSecretCipher(), "apiSecret"),
			decryptRequired(account.getPassphraseCipher(), "passphrase"),
			order
		);
		order.setStatus(result.status());
		order.setErrorCode(result.errorCode());
		order.setErrorMessage(result.errorMessage());
		tradeOrderRepository.save(order);
		String message = StringUtils.hasText(result.errorMessage())
			? result.errorMessage()
			: (result.success() ? "order canceled" : "cancel failed");
		return new TradeOrderCancelView(order.getId(), order.getStatus(), message, Instant.now());
	}

	@Override
	@Transactional(readOnly = true)
	public TradeOrderView get(Long orderId) {
		TradeOrderEntity entity = tradeOrderRepository.findById(orderId)
			.orElseThrow(() -> new NoSuchElementException("trade order not found: " + orderId));
		return toView(entity);
	}

	@Override
	@Transactional(readOnly = true)
	public List<TradeOrderView> list(TradeOrderQuery query) {
		int limit = query.limit() == null ? 100 : Math.max(1, Math.min(500, query.limit()));
		String normalizedStatus = StringUtils.hasText(query.status()) ? query.status().trim().toUpperCase(Locale.ROOT) : null;
		String normalizedSymbol = StringUtils.hasText(query.symbol()) ? query.symbol().trim().toLowerCase(Locale.ROOT) : null;
		return tradeOrderRepository.listByFilters(query.accountId(), normalizedStatus, normalizedSymbol, PageRequest.of(0, limit))
			.stream().map(this::toView).toList();
	}

	@Override
	@Transactional
	public TradeOrderView refresh(Long orderId, Long operatorUserId) {
		TradeOrderEntity order = tradeOrderRepository.findById(orderId)
			.orElseThrow(() -> new NoSuchElementException("trade order not found: " + orderId));
		TradeAccountEntity account = tradeAccountRepository.findById(order.getAccountId())
			.orElseThrow(() -> new NoSuchElementException("trade account not found: " + order.getAccountId()));
		TradeOrderProvider provider = requireProvider(account.getProvider());
		String decryptedApiSecret = decryptRequired(account.getApiSecretCipher(), "apiSecret");
		String decryptedPhrase = decryptRequired(account.getPassphraseCipher(), "passphrase");
		TradeProviderOrderState state = provider.queryOrder(account, decryptedApiSecret, decryptedPhrase, order);
		if (state.success()) {
			order.setStatus(state.status());
			order.setProviderOrderId(state.providerOrderId());
			order.setAvgFillPrice(state.avgFillPrice());
			if (state.filledQuantity() != null) {
				order.setFilledQuantity(state.filledQuantity());
			}
			if (state.filledAmount() != null) {
				order.setFilledAmount(state.filledAmount());
			}
			order.setErrorCode(null);
			order.setErrorMessage(null);
			syncFills(order, provider.listFills(account, decryptedApiSecret, decryptedPhrase, order));
		} else {
			order.setErrorCode(state.errorCode());
			order.setErrorMessage(state.errorMessage());
		}
		return toView(tradeOrderRepository.save(order));
	}

	@Override
	@Transactional(readOnly = true)
	public List<TradeFillView> listFills(Long orderId) {
		if (!tradeOrderRepository.existsById(orderId)) {
			throw new NoSuchElementException("trade order not found: " + orderId);
		}
		return tradeFillRepository.findByOrderIdOrderByFilledAtAscIdAsc(orderId)
			.stream().map(this::toFillView).toList();
	}

	private void validateCreateCommand(TradeOrderCreateCommand command) {
		if (command.accountId() == null) {
			throw new IllegalArgumentException("accountId is required");
		}
		if (!StringUtils.hasText(command.symbol())) {
			throw new IllegalArgumentException("symbol is required");
		}
		if (!StringUtils.hasText(command.side())) {
			throw new IllegalArgumentException("side is required");
		}
		if (!StringUtils.hasText(command.orderType())) {
			throw new IllegalArgumentException("orderType is required");
		}
		if (command.quantity() == null || command.quantity().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("quantity must be > 0");
		}
		if ("LIMIT".equalsIgnoreCase(command.orderType()) && (command.price() == null || command.price().compareTo(BigDecimal.ZERO) <= 0)) {
			throw new IllegalArgumentException("price must be > 0 for limit order");
		}
	}

	private TradeOrderProvider requireProvider(String providerName) {
		TradeOrderProvider provider = providerMap.get(providerName == null ? null : providerName.toUpperCase(Locale.ROOT));
		if (provider == null) {
			throw new IllegalArgumentException("unsupported trade provider: " + providerName);
		}
		return provider;
	}

	private String decryptRequired(String cipherText, String fieldName) {
		if (!StringUtils.hasText(cipherText)) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		return aesGcmCryptoUtil.decrypt(cipherText);
	}

	private String normalizeClientOrderId(String clientOrderId) {
		if (StringUtils.hasText(clientOrderId)) {
			return clientOrderId.trim();
		}
		return "cs-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
	}

	private TradeOrderEntity save(TradeOrderEntity entity) {
		try {
			return tradeOrderRepository.save(entity);
		} catch (DataIntegrityViolationException ex) {
			throw new IllegalArgumentException("trade order already exists: clientOrderId=" + entity.getClientOrderId());
		}
	}

	private void syncFills(TradeOrderEntity order, List<TradeProviderFillState> fills) {
		for (TradeProviderFillState fill : fills) {
			if (!StringUtils.hasText(fill.providerFillId())) {
				continue;
			}
			TradeFillEntity entity = tradeFillRepository.findByOrderIdAndProviderFillId(order.getId(), fill.providerFillId())
				.orElseGet(TradeFillEntity::new);
			entity.setOrderId(order.getId());
			entity.setProviderFillId(fill.providerFillId());
			entity.setSymbol(fill.symbol());
			entity.setSide(fill.side());
			entity.setPrice(fill.price() == null ? BigDecimal.ZERO : fill.price());
			entity.setQuantity(fill.quantity() == null ? BigDecimal.ZERO : fill.quantity());
			entity.setFee(fill.fee());
			entity.setFeeCurrency(fill.feeCurrency());
			entity.setFilledAt(fill.filledAt());
			tradeFillRepository.save(entity);
		}
	}

	private TradeOrderView toView(TradeOrderEntity entity) {
		return new TradeOrderView(
			entity.getId(),
			entity.getAccountId(),
			entity.getClientOrderId(),
			entity.getProvider(),
			entity.getMarketType(),
			entity.getSymbol(),
			entity.getSide(),
			entity.getOrderType(),
			entity.getPrice(),
			entity.getQuantity(),
			entity.getQuoteAmount(),
			entity.getStatus(),
			entity.getProviderOrderId(),
			entity.getAvgFillPrice(),
			entity.getFilledQuantity(),
			entity.getFilledAmount(),
			entity.getErrorCode(),
			entity.getErrorMessage(),
			entity.getCreatedBy(),
			entity.getCreatedAt(),
			entity.getUpdatedAt()
		);
	}

	private TradeFillView toFillView(TradeFillEntity entity) {
		return new TradeFillView(
			entity.getId(),
			entity.getOrderId(),
			entity.getProviderFillId(),
			entity.getSymbol(),
			entity.getSide(),
			entity.getPrice(),
			entity.getQuantity(),
			entity.getFee(),
			entity.getFeeCurrency(),
			entity.getFilledAt()
		);
	}
}
