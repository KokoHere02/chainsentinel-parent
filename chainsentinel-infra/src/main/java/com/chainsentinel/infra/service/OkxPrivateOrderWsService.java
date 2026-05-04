package com.chainsentinel.infra.service;

import com.chainsentinel.common.crypto.AesGcmCryptoUtil;
import com.chainsentinel.infra.entity.TradeAccountEntity;
import com.chainsentinel.infra.entity.TradeFillEntity;
import com.chainsentinel.infra.entity.TradeOrderEntity;
import com.chainsentinel.infra.repository.TradeAccountRepository;
import com.chainsentinel.infra.repository.TradeFillRepository;
import com.chainsentinel.infra.repository.TradeOrderRepository;
import com.chainsentinel.price.stream.ws.SimpleWebSocketClient;
import com.chainsentinel.price.stream.ws.WebSocketMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class OkxPrivateOrderWsService implements TradeOrderStreamManager {

	private static final Logger log = LoggerFactory.getLogger(OkxPrivateOrderWsService.class);
	private static final String OKX_PRIVATE_WS_URL = "wss://ws.okx.com:8443/ws/v5/private";
	private static final String PROVIDER_OKX = "OKX";

	private final TradeAccountRepository tradeAccountRepository;
	private final TradeOrderRepository tradeOrderRepository;
	private final TradeFillRepository tradeFillRepository;
	private final DefaultTradeAccountAssetService tradeAccountAssetService;
	private final AesGcmCryptoUtil aesGcmCryptoUtil;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;
	private final Map<Long, AccountSession> sessions = new ConcurrentHashMap<>();

	public OkxPrivateOrderWsService(
		TradeAccountRepository tradeAccountRepository,
		TradeOrderRepository tradeOrderRepository,
		TradeFillRepository tradeFillRepository,
		DefaultTradeAccountAssetService tradeAccountAssetService,
		AesGcmCryptoUtil aesGcmCryptoUtil,
		ObjectMapper objectMapper,
		TransactionTemplate transactionTemplate
	) {
		this.tradeAccountRepository = tradeAccountRepository;
		this.tradeOrderRepository = tradeOrderRepository;
		this.tradeFillRepository = tradeFillRepository;
		this.tradeAccountAssetService = tradeAccountAssetService;
		this.aesGcmCryptoUtil = aesGcmCryptoUtil;
		this.objectMapper = objectMapper;
		this.transactionTemplate = transactionTemplate;
	}

	@PostConstruct
	public void init() {
		for (TradeAccountEntity account : tradeAccountRepository.findByEnabledTrue()) {
			syncAccount(account.getId());
		}
	}

	@PreDestroy
	public void shutdown() {
		for (Long accountId : new ArrayList<>(sessions.keySet())) {
			disconnectAccount(accountId);
		}
	}

	@Override
	public void syncAccount(Long accountId) {
		if (accountId == null) {
			return;
		}
		disconnectAccount(accountId);
		tradeAccountRepository.findById(accountId)
			.filter(account -> Boolean.TRUE.equals(account.getEnabled()))
			.filter(account -> PROVIDER_OKX.equalsIgnoreCase(account.getProvider()))
			.filter(this::hasCredentials)
			.ifPresent(this::connect);
	}

	@Override
	public void disconnectAccount(Long accountId) {
		AccountSession session = sessions.remove(accountId);
		if (session == null) {
			return;
		}
		session.shutdown();
		log.info("trade.ws.okx.disconnected accountId={}", accountId);
	}

	@Override
	public TradeOrderStreamStatus status(Long accountId) {
		if (accountId == null) {
			return null;
		}
		AccountSession session = sessions.get(accountId);
		if (session != null) {
			return session.toStatus();
		}
		return tradeAccountRepository.findById(accountId)
			.map(this::toInactiveStatus)
			.orElse(null);
	}

	@Override
	public List<TradeOrderStreamStatus> statuses() {
		List<TradeOrderStreamStatus> result = new ArrayList<>();
		for (TradeAccountEntity account : tradeAccountRepository.findAll()) {
			AccountSession session = sessions.get(account.getId());
			result.add(session != null ? session.toStatus() : toInactiveStatus(account));
		}
		return result;
	}

	private boolean hasCredentials(TradeAccountEntity account) {
		return StringUtils.hasText(account.getApiKey())
			&& StringUtils.hasText(account.getApiSecretCipher())
			&& StringUtils.hasText(account.getPassphraseCipher());
	}

	private void connect(TradeAccountEntity account) {
		String decryptedApiSecret = aesGcmCryptoUtil.decrypt(account.getApiSecretCipher());
		String decryptedPhrase = aesGcmCryptoUtil.decrypt(account.getPassphraseCipher());
		AccountSession session = new AccountSession(account, decryptedApiSecret, decryptedPhrase);
		sessions.put(account.getId(), session);
		session.connect();
	}

	private void handleText(AccountSession session, String text) {
		session.lastMessageAt = System.currentTimeMillis();
		if (!StringUtils.hasText(text)) {
			return;
		}
		String trimmed = text.trim();
		if ("ping".equalsIgnoreCase(trimmed)) {
			session.client.sendText("pong");
			return;
		}
		if ("pong".equalsIgnoreCase(trimmed)) {
			return;
		}
		try {
			JsonNode root = objectMapper.readTree(trimmed);
			String event = root.path("event").asText("");
			if (StringUtils.hasText(event)) {
				handleEvent(session, root, event);
				return;
			}
			String channel = root.path("arg").path("channel").asText("");
			if ("orders".equals(channel)) {
				session.lastOrderMessageAt = System.currentTimeMillis();
				for (JsonNode item : root.path("data")) {
					handleOrderPush(session.accountId, item);
				}
				return;
			}
			if ("balance_and_position".equals(channel)) {
				session.lastAssetMessageAt = System.currentTimeMillis();
				handleBalanceAndPositionPush(session.accountId, root.path("data"));
			}
		} catch (Exception ex) {
			log.warn("trade.ws.okx.message.parse.failed accountId={} error={}", session.accountId, ex.getMessage());
		}
	}

	private void handleEvent(AccountSession session, JsonNode root, String event) throws Exception {
		if ("login".equalsIgnoreCase(event)) {
			if (!"0".equals(root.path("code").asText("0"))) {
				session.lastErrorType = "LOGIN_REJECTED";
				session.lastErrorMessage = root.path("msg").asText("");
				log.warn("trade.ws.okx.login.rejected accountId={} code={} msg={}",
					session.accountId, root.path("code").asText(""), root.path("msg").asText(""));
				return;
			}
			session.loggedIn = true;
			session.client.sendText(buildSubscribePayload());
			log.info("trade.ws.okx.login.ok accountId={}", session.accountId);
			return;
		}
		if ("subscribe".equalsIgnoreCase(event)) {
			String channel = root.path("arg").path("channel").asText("");
			if ("orders".equals(channel)) {
				session.orderSubscribed = true;
			}
			if ("balance_and_position".equals(channel)) {
				session.assetSubscribed = true;
			}
			log.info("trade.ws.okx.subscribe.ok accountId={} channel={} instType={}",
				session.accountId,
				channel,
				root.path("arg").path("instType").asText(""));
			return;
		}
		if ("error".equalsIgnoreCase(event)) {
			session.lastErrorType = "EVENT_ERROR";
			session.lastErrorMessage = root.path("msg").asText("");
			log.warn("trade.ws.okx.event.error accountId={} code={} msg={}",
				session.accountId, root.path("code").asText(""), root.path("msg").asText(""));
		}
	}

	private void handleOrderPush(Long accountId, JsonNode item) {
		transactionTemplate.executeWithoutResult(status -> {
			String providerOrderId = textOrNull(item.path("ordId"));
			String clientOrderId = textOrNull(item.path("clOrdId"));
			TradeOrderEntity order = resolveOrder(accountId, providerOrderId, clientOrderId);
			if (order == null) {
				log.debug("trade.ws.okx.order.skip accountId={} ordId={} clOrdId={}", accountId, providerOrderId, clientOrderId);
				return;
			}
			order.setProviderOrderId(firstNonBlank(providerOrderId, order.getProviderOrderId()));
			order.setStatus(mapOrderState(textOrNull(item.path("state"))));
			order.setAvgFillPrice(toDecimal(textOrNull(item.path("avgPx"))));
			order.setFilledQuantity(defaultZero(toDecimal(textOrNull(item.path("accFillSz")))));
			order.setFilledAmount(resolveFilledAmount(order.getAvgFillPrice(), order.getFilledQuantity()));
			order.setErrorCode(null);
			order.setErrorMessage(null);
			tradeOrderRepository.save(order);
			upsertFillIfPresent(order, item);
		});
	}

	private void handleBalanceAndPositionPush(Long accountId, JsonNode data) {
		transactionTemplate.executeWithoutResult(status -> {
			List<TradeAssetBalanceItem> balances = new ArrayList<>();
			for (JsonNode item : data) {
				for (JsonNode balance : item.path("balData")) {
					String asset = textOrNull(balance.path("ccy"));
					if (!StringUtils.hasText(asset)) {
						continue;
					}
					balances.add(new TradeAssetBalanceItem(
						asset.toUpperCase(Locale.ROOT),
						defaultZero(firstNonNullDecimal(
							toDecimal(textOrNull(balance.path("availBal"))),
							toDecimal(textOrNull(balance.path("cashBal")))
						)),
						defaultZero(toDecimal(textOrNull(balance.path("frozenBal")))),
						defaultZero(firstNonNullDecimal(
							toDecimal(textOrNull(balance.path("cashBal"))),
							toDecimal(textOrNull(balance.path("availBal")))
						))
					));
				}
			}
			if (!balances.isEmpty()) {
				tradeAccountAssetService.snapshotFromBalances(accountId, balances, Instant.now(), "WS");
			}
		});
	}

	private TradeOrderEntity resolveOrder(Long accountId, String providerOrderId, String clientOrderId) {
		if (StringUtils.hasText(providerOrderId)) {
			TradeOrderEntity order = tradeOrderRepository
				.findByProviderAndProviderOrderId(PROVIDER_OKX, providerOrderId)
				.orElse(null);
			if (order != null) {
				return order;
			}
		}
		if (accountId != null && StringUtils.hasText(clientOrderId)) {
			return tradeOrderRepository.findByAccountIdAndClientOrderId(accountId, clientOrderId).orElse(null);
		}
		return null;
	}

	private void upsertFillIfPresent(TradeOrderEntity order, JsonNode item) {
		String tradeId = textOrNull(item.path("tradeId"));
		if (!StringUtils.hasText(tradeId)) {
			return;
		}
		TradeFillEntity fill = tradeFillRepository.findByOrderIdAndProviderFillId(order.getId(), tradeId)
			.orElseGet(TradeFillEntity::new);
		fill.setOrderId(order.getId());
		fill.setProviderFillId(tradeId);
		fill.setSymbol(firstNonBlank(textOrNull(item.path("instId")), order.getSymbol()));
		fill.setSide(firstNonBlank(upper(textOrNull(item.path("side"))), order.getSide()));
		fill.setPrice(defaultZero(toDecimal(textOrNull(item.path("fillPx")))));
		fill.setQuantity(defaultZero(toDecimal(textOrNull(item.path("fillSz")))));
		fill.setFee(toDecimal(textOrNull(item.path("fillFee"))));
		fill.setFeeCurrency(textOrNull(item.path("fillFeeCcy")));
		fill.setFilledAt(toInstant(textOrNull(item.path("fillTime"))));
		tradeFillRepository.save(fill);
	}

	private String buildLoginPayload(AccountSession session) throws Exception {
		long unixSeconds = Instant.now().getEpochSecond();
		String timestamp = String.valueOf(unixSeconds);
		Map<String, Object> args = new LinkedHashMap<>();
		args.put("apiKey", session.accessKeyValue);
		args.put("passphrase", session.phraseValue);
		args.put("timestamp", timestamp);
		args.put("sign", sign(timestamp + "GET" + "/users/self/verify", session.apiSecret));
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("op", "login");
		payload.put("args", List.of(args));
		return objectMapper.writeValueAsString(payload);
	}

	private String buildSubscribePayload() throws Exception {
		Map<String, Object> ordersArg = new LinkedHashMap<>();
		ordersArg.put("channel", "orders");
		ordersArg.put("instType", "SPOT");
		Map<String, Object> assetArg = new LinkedHashMap<>();
		assetArg.put("channel", "balance_and_position");
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("op", "subscribe");
		payload.put("args", List.of(ordersArg, assetArg));
		return objectMapper.writeValueAsString(payload);
	}

	private String sign(String payload, String secret) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
	}

	private String mapOrderState(String state) {
		return switch (Objects.toString(state, "").toLowerCase(Locale.ROOT)) {
			case "live" -> "SUBMITTED";
			case "partially_filled" -> "PARTIALLY_FILLED";
			case "filled" -> "FILLED";
			case "canceled", "mmp_canceled" -> "CANCELED";
			case "order_failed" -> "FAILED";
			default -> "FAILED";
		};
	}

	private java.math.BigDecimal toDecimal(String text) {
		if (!StringUtils.hasText(text)) {
			return null;
		}
		return new java.math.BigDecimal(text);
	}

	private java.math.BigDecimal resolveFilledAmount(java.math.BigDecimal avgFillPrice, java.math.BigDecimal filledQuantity) {
		if (avgFillPrice == null || filledQuantity == null) {
			return java.math.BigDecimal.ZERO;
		}
		return avgFillPrice.multiply(filledQuantity);
	}

	private java.math.BigDecimal defaultZero(java.math.BigDecimal value) {
		return value == null ? java.math.BigDecimal.ZERO : value;
	}

	private java.math.BigDecimal firstNonNullDecimal(java.math.BigDecimal first, java.math.BigDecimal second) {
		return first != null ? first : second;
	}

	private Instant toInstant(String millisText) {
		if (!StringUtils.hasText(millisText)) {
			return null;
		}
		return Instant.ofEpochMilli(Long.parseLong(millisText));
	}

	private String textOrNull(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		String value = node.asText();
		return StringUtils.hasText(value) ? value : null;
	}

	private String upper(String value) {
		return value == null ? null : value.toUpperCase(Locale.ROOT);
	}

	private String firstNonBlank(String first, String second) {
		return StringUtils.hasText(first) ? first : second;
	}

	private TradeOrderStreamStatus toInactiveStatus(TradeAccountEntity account) {
		return new TradeOrderStreamStatus(
			account.getId(),
			account.getProvider(),
			account.getEnabled(),
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

	private final class AccountSession {

		private final Long accountId;
		private final String accessKeyValue;
		private final String apiSecret;
		private final String phraseValue;
		private final SimpleWebSocketClient client = new SimpleWebSocketClient(Duration.ofSeconds(10));
		private final ScheduledExecutorService keepaliveExecutor;
		private volatile long lastMessageAt;
		private volatile long lastOrderMessageAt;
		private volatile long lastAssetMessageAt;
		private volatile boolean loggedIn;
		private volatile boolean orderSubscribed;
		private volatile boolean assetSubscribed;
		private volatile String lastErrorType;
		private volatile String lastErrorMessage;

		private AccountSession(TradeAccountEntity account, String apiSecret, String phraseValue) {
			this.accountId = account.getId();
			this.accessKeyValue = account.getApiKey();
			this.apiSecret = apiSecret;
			this.phraseValue = phraseValue;
			this.keepaliveExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable);
				thread.setName("okx-private-order-ws-" + accountId);
				thread.setDaemon(true);
				return thread;
			});
		}

		private void connect() {
			client.connect(OKX_PRIVATE_WS_URL, new WebSocketMessageHandler() {
				@Override
				public void onOpen() {
					lastMessageAt = System.currentTimeMillis();
					lastOrderMessageAt = 0L;
					lastAssetMessageAt = 0L;
					loggedIn = false;
					orderSubscribed = false;
					assetSubscribed = false;
					lastErrorType = null;
					lastErrorMessage = null;
					try {
						client.sendText(buildLoginPayload(AccountSession.this));
						startKeepalive();
						log.info("trade.ws.okx.connected accountId={}", accountId);
					} catch (Exception ex) {
						log.warn("trade.ws.okx.login.send.failed accountId={} error={}", accountId, ex.getMessage());
					}
				}

				@Override
				public void onText(String text) {
					handleText(AccountSession.this, text);
				}

				@Override
				public void onClose(int statusCode, String reason) {
					loggedIn = false;
					orderSubscribed = false;
					assetSubscribed = false;
					lastErrorType = "WS_CLOSED";
					lastErrorMessage = reason;
					log.warn("trade.ws.okx.closed accountId={} status={} reason={}", accountId, statusCode, reason);
					startReconnect();
				}

				@Override
				public void onError(Throwable error) {
					loggedIn = false;
					orderSubscribed = false;
					assetSubscribed = false;
					lastErrorType = error == null ? "WS_ERROR" : error.getClass().getSimpleName();
					lastErrorMessage = error == null ? null : error.getMessage();
					log.warn("trade.ws.okx.error accountId={} error={}", accountId, error == null ? null : error.getMessage());
					startReconnect();
				}
			});
		}

		private void startKeepalive() {
			keepaliveExecutor.scheduleAtFixedRate(() -> {
				if (!client.isConnected()) {
					return;
				}
				long idleMs = System.currentTimeMillis() - lastMessageAt;
				if (idleMs >= 20000L) {
					client.sendText("ping");
				}
			}, 10, 10, TimeUnit.SECONDS);
		}

		private void startReconnect() {
			if (!sessions.containsKey(accountId)) {
				return;
			}
			fallbackSyncAssets();
			keepaliveExecutor.schedule(() -> {
				if (!sessions.containsKey(accountId)) {
					return;
				}
				syncAccount(accountId);
			}, 3, TimeUnit.SECONDS);
		}

		private void shutdown() {
			keepaliveExecutor.shutdownNow();
			client.close();
		}

		private void fallbackSyncAssets() {
			try {
				tradeAccountAssetService.syncFallback(accountId);
			} catch (Exception ex) {
				log.warn("trade.ws.okx.asset.http_fallback.failed accountId={} error={}", accountId, ex.getMessage());
			}
		}

		private TradeOrderStreamStatus toStatus() {
			return new TradeOrderStreamStatus(
				accountId,
				PROVIDER_OKX,
				true,
				client.isConnected(),
				loggedIn,
				orderSubscribed,
				assetSubscribed,
				lastMessageAt <= 0L ? null : Instant.ofEpochMilli(lastMessageAt),
				lastOrderMessageAt <= 0L ? null : Instant.ofEpochMilli(lastOrderMessageAt),
				lastAssetMessageAt <= 0L ? null : Instant.ofEpochMilli(lastAssetMessageAt),
				lastErrorType,
				lastErrorMessage
			);
		}
	}
}
