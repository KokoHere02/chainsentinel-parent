package com.chainsentinel.infra.service;

import com.chainsentinel.common.crypto.AesGcmCryptoUtil;
import com.chainsentinel.core.service.TradeAccountAssetService;
import com.chainsentinel.core.service.dto.TradeAccountAssetSyncView;
import com.chainsentinel.core.service.dto.TradeAccountBalanceSnapshotView;
import com.chainsentinel.core.service.dto.TradePositionSnapshotView;
import com.chainsentinel.infra.entity.TradeAccountBalanceSnapshotEntity;
import com.chainsentinel.infra.entity.TradeAccountEntity;
import com.chainsentinel.infra.entity.TradePositionSnapshotEntity;
import com.chainsentinel.infra.repository.TradeAccountBalanceSnapshotRepository;
import com.chainsentinel.infra.repository.TradeAccountRepository;
import com.chainsentinel.infra.repository.TradeFillRepository;
import com.chainsentinel.infra.repository.TradePositionSnapshotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class DefaultTradeAccountAssetService implements TradeAccountAssetService {

	private static final String OKX_BASE_URL = "https://www.okx.com";
	private static final String OKX_BALANCE_PATH = "/api/v5/account/balance";
	private static final String OKX_TICKER_PATH = "/api/v5/market/ticker";
	private static final String SNAPSHOT_SOURCE_HTTP = "HTTP";
	private static final String SNAPSHOT_SOURCE_HTTP_FALLBACK = "HTTP_FALLBACK";
	private static final String SNAPSHOT_SOURCE_WS = "WS";

	private final TradeAccountRepository tradeAccountRepository;
	private final TradeAccountBalanceSnapshotRepository balanceSnapshotRepository;
	private final TradePositionSnapshotRepository positionSnapshotRepository;
	private final TradeFillRepository tradeFillRepository;
	private final AesGcmCryptoUtil aesGcmCryptoUtil;
	private final RestTemplateBuilder restTemplateBuilder;
	private final ObjectMapper objectMapper;

	public DefaultTradeAccountAssetService(
		TradeAccountRepository tradeAccountRepository,
		TradeAccountBalanceSnapshotRepository balanceSnapshotRepository,
		TradePositionSnapshotRepository positionSnapshotRepository,
		TradeFillRepository tradeFillRepository,
		AesGcmCryptoUtil aesGcmCryptoUtil,
		RestTemplateBuilder restTemplateBuilder,
		ObjectMapper objectMapper
	) {
		this.tradeAccountRepository = tradeAccountRepository;
		this.balanceSnapshotRepository = balanceSnapshotRepository;
		this.positionSnapshotRepository = positionSnapshotRepository;
		this.tradeFillRepository = tradeFillRepository;
		this.aesGcmCryptoUtil = aesGcmCryptoUtil;
		this.restTemplateBuilder = restTemplateBuilder;
		this.objectMapper = objectMapper;
	}

	@Override
	@Transactional
	public TradeAccountAssetSyncView sync(Long accountId, Long operatorUserId) {
		return syncInternal(accountId, SNAPSHOT_SOURCE_HTTP);
	}

	@Transactional
	public TradeAccountAssetSyncView syncFallback(Long accountId) {
		return syncInternal(accountId, SNAPSHOT_SOURCE_HTTP_FALLBACK);
	}

	private TradeAccountAssetSyncView syncInternal(Long accountId, String source) {
		TradeAccountEntity account = tradeAccountRepository.findById(accountId)
			.orElseThrow(() -> new java.util.NoSuchElementException("trade account not found: " + accountId));
		if (!"OKX".equalsIgnoreCase(account.getProvider())) {
			throw new IllegalArgumentException("unsupported trade provider: " + account.getProvider());
		}
		String decryptedApiSecret = decryptRequired(account.getApiSecretCipher(), "apiSecret");
		String decryptedPhrase = decryptRequired(account.getPassphraseCipher(), "passphrase");
		Instant snapshotTime = Instant.now();
		List<TradeAssetBalanceItem> balances = fetchBalances(account, decryptedApiSecret, decryptedPhrase);
		return snapshotFromBalances(account.getId(), balances, snapshotTime, source);
	}

	@Transactional
	public TradeAccountAssetSyncView snapshotFromBalances(Long accountId, List<TradeAssetBalanceItem> balances, Instant snapshotTime) {
		return snapshotFromBalances(accountId, balances, snapshotTime, SNAPSHOT_SOURCE_HTTP);
	}

	@Transactional
	public TradeAccountAssetSyncView snapshotFromBalances(Long accountId, List<TradeAssetBalanceItem> balances, Instant snapshotTime, String source) {
		requireAccount(accountId);
		List<TradeAssetBalanceItem> mergedBalances = mergeBalancesWithLatestSnapshot(accountId, balances);
		Map<String, PositionSnapshotDraft> positionDraftMap = buildPositionDrafts(accountId, mergedBalances);
		if (isSameAsLatestBalances(accountId, mergedBalances, source) && isSameAsLatestPositions(accountId, positionDraftMap, source)) {
			return new TradeAccountAssetSyncView(accountId, 0, 0, snapshotTime);
		}
		List<TradeAccountBalanceSnapshotEntity> balanceEntities = persistBalances(accountId, mergedBalances, snapshotTime, source);
		List<TradePositionSnapshotEntity> positionEntities = persistPositions(accountId, positionDraftMap, snapshotTime, source);
		return new TradeAccountAssetSyncView(accountId, balanceEntities.size(), positionEntities.size(), snapshotTime);
	}

	@Override
	@Transactional(readOnly = true)
	public List<TradeAccountBalanceSnapshotView> listLatestBalances(Long accountId) {
		requireAccount(accountId);
		Instant latest = balanceSnapshotRepository.findLatestSnapshotTimeByAccountId(accountId);
		if (latest == null) {
			return List.of();
		}
		return balanceSnapshotRepository.findByAccountIdAndSnapshotTimeOrderByAssetAsc(accountId, latest).stream()
			.map(this::toBalanceView)
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<TradePositionSnapshotView> listLatestPositions(Long accountId) {
		requireAccount(accountId);
		Instant latest = positionSnapshotRepository.findLatestSnapshotTimeByAccountId(accountId);
		if (latest == null) {
			return List.of();
		}
		return positionSnapshotRepository.findByAccountIdAndSnapshotTimeOrderBySymbolAsc(accountId, latest).stream()
			.map(this::toPositionView)
			.toList();
	}

	private void requireAccount(Long accountId) {
		if (!tradeAccountRepository.existsById(accountId)) {
			throw new java.util.NoSuchElementException("trade account not found: " + accountId);
		}
	}

	private List<TradeAssetBalanceItem> fetchBalances(TradeAccountEntity account, String apiSecret, String passphrase) {
		try {
			String timestamp = Instant.now().toString();
			HttpHeaders headers = buildSignedGetHeaders(account, apiSecret, passphrase, timestamp, OKX_BALANCE_PATH);
			RestTemplate restTemplate = restTemplateBuilder.build();
			ResponseEntity<String> response = restTemplate.exchange(
				OKX_BASE_URL + OKX_BALANCE_PATH,
				HttpMethod.GET,
				new HttpEntity<>(headers),
				String.class
			);
			return parseBalances(response.getBody());
		} catch (Exception ex) {
			throw new IllegalStateException("sync trade account assets failed: " + sanitize(ex.getMessage()), ex);
		}
	}

	private List<TradeAccountBalanceSnapshotEntity> persistBalances(Long accountId, List<TradeAssetBalanceItem> balances, Instant snapshotTime, String source) {
		List<TradeAccountBalanceSnapshotEntity> result = new ArrayList<>();
		for (TradeAssetBalanceItem item : balances) {
			TradeAccountBalanceSnapshotEntity entity = new TradeAccountBalanceSnapshotEntity();
			entity.setAccountId(accountId);
			entity.setAsset(item.asset());
			entity.setAvailable(item.available());
			entity.setFrozen(item.frozen());
			entity.setTotal(item.total());
			entity.setSource(source);
			entity.setSnapshotTime(snapshotTime);
			result.add(balanceSnapshotRepository.save(entity));
		}
		return result;
	}

	private List<TradePositionSnapshotEntity> persistPositions(Long accountId, Map<String, PositionSnapshotDraft> positionDraftMap, Instant snapshotTime, String source) {
		List<TradePositionSnapshotEntity> result = new ArrayList<>();
		for (PositionSnapshotDraft draft : positionDraftMap.values()) {
			TradePositionSnapshotEntity entity = new TradePositionSnapshotEntity();
			entity.setAccountId(accountId);
			entity.setSymbol(draft.symbol());
			entity.setBaseAsset(draft.baseAsset());
			entity.setQuoteAsset(draft.quoteAsset());
			entity.setQuantity(draft.quantity());
			entity.setAvgCost(draft.avgCost());
			entity.setMarketPrice(draft.marketPrice());
			entity.setMarketValue(draft.marketValue());
			entity.setUnrealizedPnl(calculateUnrealizedPnl(entity.getAvgCost(), entity.getMarketPrice(), entity.getQuantity()));
			entity.setUnrealizedPnlRatio(calculateUnrealizedPnlRatio(entity.getAvgCost(), entity.getMarketPrice()));
			entity.setSource(source);
			entity.setSnapshotTime(snapshotTime);
			result.add(positionSnapshotRepository.save(entity));
		}
		return result;
	}

	private List<TradeAssetBalanceItem> mergeBalancesWithLatestSnapshot(Long accountId, List<TradeAssetBalanceItem> incomingBalances) {
		Map<String, TradeAssetBalanceItem> merged = new LinkedHashMap<>();
		Instant latest = balanceSnapshotRepository.findLatestSnapshotTimeByAccountId(accountId);
		if (latest != null) {
			for (TradeAccountBalanceSnapshotEntity entity : balanceSnapshotRepository.findByAccountIdAndSnapshotTimeOrderByAssetAsc(accountId, latest)) {
			merged.put(entity.getAsset(), new TradeAssetBalanceItem(
				entity.getAsset(),
				entity.getAvailable(),
					entity.getFrozen(),
					entity.getTotal()
				));
			}
		}
		for (TradeAssetBalanceItem item : incomingBalances) {
			merged.put(item.asset(), item);
		}
		return merged.values().stream()
			.filter(item -> StringUtils.hasText(item.asset()))
			.sorted(java.util.Comparator.comparing(TradeAssetBalanceItem::asset))
			.toList();
	}

	private Map<String, PositionSnapshotDraft> buildPositionDrafts(Long accountId, List<TradeAssetBalanceItem> balances) {
		Map<String, PositionSnapshotDraft> result = new LinkedHashMap<>();
		Map<String, BigDecimal> priceCache = new LinkedHashMap<>();
		Map<String, BigDecimal> avgCostMap = calculateAvgCosts(accountId);
		for (TradeAssetBalanceItem item : balances) {
			if (item.total().compareTo(BigDecimal.ZERO) <= 0 || isQuoteAsset(item.asset())) {
				continue;
			}
			BigDecimal price = resolveMarketPrice(item.asset(), priceCache);
			result.put(item.asset() + "-USDT", new PositionSnapshotDraft(
				item.asset() + "-USDT",
				item.asset(),
				"USDT",
				item.total(),
				avgCostMap.get(item.asset()),
				price,
				price == null ? null : price.multiply(item.total())
			));
		}
		return result;
	}

	private boolean isSameAsLatestBalances(Long accountId, List<TradeAssetBalanceItem> balances, String source) {
		Instant latest = balanceSnapshotRepository.findLatestSnapshotTimeByAccountId(accountId);
		if (latest == null) {
			return false;
		}
		List<TradeAccountBalanceSnapshotEntity> previous = balanceSnapshotRepository.findByAccountIdAndSnapshotTimeOrderByAssetAsc(accountId, latest);
		if (!previous.isEmpty() && !sameText(previous.get(0).getSource(), source)) {
			return false;
		}
		if (previous.size() != balances.size()) {
			return false;
		}
		for (int i = 0; i < previous.size(); i++) {
			TradeAccountBalanceSnapshotEntity left = previous.get(i);
			TradeAssetBalanceItem right = balances.get(i);
			if (!sameText(left.getAsset(), right.asset())
				|| !sameDecimal(left.getAvailable(), right.available())
				|| !sameDecimal(left.getFrozen(), right.frozen())
				|| !sameDecimal(left.getTotal(), right.total())) {
				return false;
			}
		}
		return true;
	}

	private boolean isSameAsLatestPositions(Long accountId, Map<String, PositionSnapshotDraft> positionDraftMap, String source) {
		Instant latest = positionSnapshotRepository.findLatestSnapshotTimeByAccountId(accountId);
		if (latest == null) {
			return positionDraftMap.isEmpty();
		}
		List<TradePositionSnapshotEntity> previous = positionSnapshotRepository.findByAccountIdAndSnapshotTimeOrderBySymbolAsc(accountId, latest);
		if (!previous.isEmpty() && !sameText(previous.get(0).getSource(), source)) {
			return false;
		}
		if (previous.size() != positionDraftMap.size()) {
			return false;
		}
		int index = 0;
		for (PositionSnapshotDraft draft : positionDraftMap.values()) {
			TradePositionSnapshotEntity entity = previous.get(index++);
			if (!sameText(entity.getSymbol(), draft.symbol())
				|| !sameText(entity.getBaseAsset(), draft.baseAsset())
				|| !sameText(entity.getQuoteAsset(), draft.quoteAsset())
				|| !sameDecimal(entity.getQuantity(), draft.quantity())
				|| !sameDecimal(entity.getAvgCost(), draft.avgCost())
				|| !sameDecimal(entity.getMarketPrice(), draft.marketPrice())
				|| !sameDecimal(entity.getMarketValue(), draft.marketValue())) {
				return false;
			}
		}
		return true;
	}

	private Map<String, BigDecimal> calculateAvgCosts(Long accountId) {
		Map<String, PositionCostState> states = new LinkedHashMap<>();
		for (var fill : tradeFillRepository.listByAccountIdOrderByFilledAtAscIdAsc(accountId)) {
			String asset = baseAsset(fill.getSymbol());
			String quoteAsset = quoteAsset(fill.getSymbol());
			if (!StringUtils.hasText(asset) || fill.getQuantity() == null || fill.getPrice() == null) {
				continue;
			}
			PositionCostState state = states.computeIfAbsent(asset, ignored -> new PositionCostState());
			BigDecimal quantity = fill.getQuantity();
			BigDecimal price = fill.getPrice();
			BigDecimal fee = defaultZero(fill.getFee()).abs();
			boolean feeInBase = sameText(asset, normalizeAsset(fill.getFeeCurrency()));
			boolean feeInQuote = sameText(quoteAsset, normalizeAsset(fill.getFeeCurrency()));
			if ("BUY".equalsIgnoreCase(fill.getSide())) {
				BigDecimal netQuantity = feeInBase ? quantity.subtract(fee) : quantity;
				if (netQuantity.compareTo(BigDecimal.ZERO) < 0) {
					netQuantity = BigDecimal.ZERO;
				}
				state.quantity = state.quantity.add(netQuantity);
				state.cost = state.cost.add(price.multiply(quantity));
				if (feeInQuote) {
					state.cost = state.cost.add(fee);
				}
				continue;
			}
			if ("SELL".equalsIgnoreCase(fill.getSide()) && state.quantity.compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal grossReduction = feeInBase ? quantity.add(fee) : quantity;
				BigDecimal sellQuantity = grossReduction.min(state.quantity);
				BigDecimal avgCost = state.cost.divide(state.quantity, 18, java.math.RoundingMode.HALF_UP);
				state.quantity = state.quantity.subtract(sellQuantity);
				state.cost = state.cost.subtract(avgCost.multiply(sellQuantity));
				if (state.quantity.compareTo(BigDecimal.ZERO) <= 0) {
					state.quantity = BigDecimal.ZERO;
					state.cost = BigDecimal.ZERO;
				}
			}
		}
		Map<String, BigDecimal> result = new LinkedHashMap<>();
		for (Map.Entry<String, PositionCostState> entry : states.entrySet()) {
			if (entry.getValue().quantity.compareTo(BigDecimal.ZERO) > 0) {
				result.put(entry.getKey(), entry.getValue().cost.divide(entry.getValue().quantity, 18, java.math.RoundingMode.HALF_UP));
			}
		}
		return result;
	}

	private BigDecimal resolveMarketPrice(String asset, Map<String, BigDecimal> priceCache) {
		if (priceCache.containsKey(asset)) {
			return priceCache.get(asset);
		}
		BigDecimal price = fetchTickerPrice(asset + "-USDT");
		if (price == null && !"USDC".equalsIgnoreCase(asset)) {
			price = fetchTickerPrice(asset + "-USDC");
		}
		priceCache.put(asset, price);
		return price;
	}

	protected BigDecimal fetchTickerPrice(String instId) {
		try {
			RestTemplate restTemplate = restTemplateBuilder.build();
			ResponseEntity<String> response = restTemplate.exchange(
				OKX_BASE_URL + OKX_TICKER_PATH + "?instId=" + instId,
				HttpMethod.GET,
				HttpEntity.EMPTY,
				String.class
			);
			JsonNode root = objectMapper.readTree(response.getBody());
			JsonNode first = root.path("data").isArray() && root.path("data").size() > 0 ? root.path("data").get(0) : null;
			if (!"0".equals(root.path("code").asText()) || first == null) {
				return null;
			}
			return toDecimal(first.path("last").asText(null));
		} catch (Exception ex) {
			return null;
		}
	}

	private List<TradeAssetBalanceItem> parseBalances(String body) throws Exception {
		if (!StringUtils.hasText(body)) {
			return List.of();
		}
		JsonNode root = objectMapper.readTree(body);
		if (!"0".equals(root.path("code").asText())) {
			throw new IllegalStateException(root.path("msg").asText("okx balance response failed"));
		}
		JsonNode first = root.path("data").isArray() && root.path("data").size() > 0 ? root.path("data").get(0) : null;
		if (first == null) {
			return List.of();
		}
		List<TradeAssetBalanceItem> result = new ArrayList<>();
		for (JsonNode item : first.path("details")) {
			String asset = item.path("ccy").asText("");
			BigDecimal available = toDecimal(item.path("availBal").asText(null));
			BigDecimal frozen = toDecimal(item.path("frozenBal").asText(null));
			BigDecimal total = toDecimal(item.path("eq").asText(null));
			if (!StringUtils.hasText(asset)) {
				continue;
			}
			result.add(new TradeAssetBalanceItem(
				asset.toUpperCase(Locale.ROOT),
				defaultZero(available),
				defaultZero(frozen),
				defaultZero(total)
			));
		}
		return result;
	}

	private HttpHeaders buildSignedGetHeaders(
		TradeAccountEntity account,
		String apiSecret,
		String passphrase,
		String timestamp,
		String pathWithQuery
	) throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.add("OK-ACCESS-KEY", account.getApiKey());
		headers.add("OK-ACCESS-PASSPHRASE", passphrase);
		headers.add("OK-ACCESS-TIMESTAMP", timestamp);
		headers.add("OK-ACCESS-SIGN", sign(timestamp + "GET" + pathWithQuery, apiSecret));
		if ("SIMULATED".equalsIgnoreCase(account.getEnvType())) {
			headers.add("x-simulated-trading", "1");
		}
		return headers;
	}

	private String sign(String payload, String secret) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
	}

	private boolean isQuoteAsset(String asset) {
		return "USDT".equalsIgnoreCase(asset) || "USDC".equalsIgnoreCase(asset);
	}

	private String baseAsset(String symbol) {
		if (!StringUtils.hasText(symbol)) {
			return null;
		}
		int index = symbol.indexOf('-');
		if (index <= 0) {
			return symbol.trim().toUpperCase(Locale.ROOT);
		}
		return symbol.substring(0, index).trim().toUpperCase(Locale.ROOT);
	}

	private String quoteAsset(String symbol) {
		if (!StringUtils.hasText(symbol)) {
			return null;
		}
		int index = symbol.indexOf('-');
		if (index < 0 || index + 1 >= symbol.length()) {
			return null;
		}
		return symbol.substring(index + 1).trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeAsset(String asset) {
		if (!StringUtils.hasText(asset)) {
			return null;
		}
		return asset.trim().toUpperCase(Locale.ROOT);
	}

	private String decryptRequired(String cipherText, String fieldName) {
		if (!StringUtils.hasText(cipherText)) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		return aesGcmCryptoUtil.decrypt(cipherText);
	}

	private BigDecimal toDecimal(String text) {
		if (!StringUtils.hasText(text)) {
			return null;
		}
		return new BigDecimal(text);
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private boolean sameDecimal(BigDecimal left, BigDecimal right) {
		if (left == null || right == null) {
			return left == right;
		}
		return left.compareTo(right) == 0;
	}

	private boolean sameText(String left, String right) {
		if (left == null || right == null) {
			return left == right;
		}
		return left.equals(right);
	}

	private String sanitize(String value) {
		return value == null ? "" : value.replace("\r", " ").replace("\n", " ");
	}

	private TradeAccountBalanceSnapshotView toBalanceView(TradeAccountBalanceSnapshotEntity entity) {
		return new TradeAccountBalanceSnapshotView(
			entity.getId(),
			entity.getAccountId(),
			entity.getAsset(),
			entity.getAvailable(),
			entity.getFrozen(),
			entity.getTotal(),
			entity.getSource(),
			entity.getSnapshotTime()
		);
	}

	private TradePositionSnapshotView toPositionView(TradePositionSnapshotEntity entity) {
		return new TradePositionSnapshotView(
			entity.getId(),
			entity.getAccountId(),
			entity.getSymbol(),
			entity.getBaseAsset(),
			entity.getQuoteAsset(),
			entity.getQuantity(),
			entity.getAvgCost(),
			entity.getMarketPrice(),
			entity.getMarketValue(),
			entity.getUnrealizedPnl(),
			entity.getUnrealizedPnlRatio(),
			entity.getSource(),
			entity.getSnapshotTime()
		);
	}

	private BigDecimal calculateUnrealizedPnl(BigDecimal avgCost, BigDecimal marketPrice, BigDecimal quantity) {
		if (avgCost == null || marketPrice == null || quantity == null) {
			return null;
		}
		return marketPrice.subtract(avgCost).multiply(quantity);
	}

	private BigDecimal calculateUnrealizedPnlRatio(BigDecimal avgCost, BigDecimal marketPrice) {
		if (avgCost == null || marketPrice == null || BigDecimal.ZERO.compareTo(avgCost) == 0) {
			return null;
		}
		return marketPrice.subtract(avgCost).divide(avgCost, 18, java.math.RoundingMode.HALF_UP);
	}

	private static final class PositionCostState {
		private BigDecimal quantity = BigDecimal.ZERO;
		private BigDecimal cost = BigDecimal.ZERO;
	}

	private record PositionSnapshotDraft(
		String symbol,
		String baseAsset,
		String quoteAsset,
		BigDecimal quantity,
		BigDecimal avgCost,
		BigDecimal marketPrice,
		BigDecimal marketValue
	) {
	}
}
