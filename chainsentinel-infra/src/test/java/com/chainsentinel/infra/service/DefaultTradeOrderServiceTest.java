package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.common.crypto.AesGcmCryptoUtil;
import com.chainsentinel.core.exception.CoreErrorCode;
import com.chainsentinel.core.exception.TradeRiskException;
import com.chainsentinel.core.service.dto.TradeFillView;
import com.chainsentinel.core.service.dto.TradeOrderCreateCommand;
import com.chainsentinel.infra.config.TradeProperties;
import com.chainsentinel.infra.entity.TradeAccountEntity;
import com.chainsentinel.infra.entity.TradeFillEntity;
import com.chainsentinel.infra.entity.TradeOrderEntity;
import com.chainsentinel.infra.repository.TradeAccountRepository;
import com.chainsentinel.infra.repository.TradeFillRepository;
import com.chainsentinel.infra.repository.TradeOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultTradeOrderServiceTest {

	private static final String BASE64_KEY = "E68oWUgtnhEu1d6sLWPdiw==";

	@Mock
	private TradeOrderRepository tradeOrderRepository;

	@Mock
	private TradeAccountRepository tradeAccountRepository;

	@Mock
	private TradeFillRepository tradeFillRepository;

	private TradeProperties tradeProperties() {
		TradeProperties properties = new TradeProperties();
		properties.setEnabled(true);
		properties.setSandboxOnly(true);
		return properties;
	}

	@Test
	void shouldCreateSubmittedOrder() {
		AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
		TradeAccountEntity account = account(cryptoUtil);
		TradeOrderProvider provider = new TradeOrderProvider() {
			@Override
			public String provider() {
				return "OKX";
			}

			@Override
			public TradeProviderSubmitResult submit(TradeAccountEntity a, String apiSecret, String passphrase, TradeOrderCreateCommand c) {
				return new TradeProviderSubmitResult(true, "ord-1", "SUBMITTED", null, null);
			}

			@Override
			public TradeProviderCancelResult cancel(TradeAccountEntity a, String apiSecret, String passphrase, TradeOrderEntity order) {
				return new TradeProviderCancelResult(true, "CANCELED", null, null);
			}

			@Override
			public TradeProviderOrderState queryOrder(TradeAccountEntity account, String apiSecret, String passphrase, TradeOrderEntity order) {
				return new TradeProviderOrderState(true, "FILLED", "ord-1", new BigDecimal("100"), new BigDecimal("0.01"), new BigDecimal("1"), null, null);
			}

			@Override
			public List<TradeProviderFillState> listFills(TradeAccountEntity account, String apiSecret, String passphrase, TradeOrderEntity order) {
				return List.of();
			}
		};
		DefaultTradeOrderService service = new DefaultTradeOrderService(
			tradeOrderRepository,
			tradeAccountRepository,
			tradeFillRepository,
			cryptoUtil,
			tradeProperties(),
			List.of(provider)
		);
		when(tradeAccountRepository.findById(1L)).thenReturn(Optional.of(account));
		when(tradeOrderRepository.save(any(TradeOrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var result = service.create(new TradeOrderCreateCommand(
			1L, "BTC-USDT", "BUY", "MARKET", null, new BigDecimal("0.01"), null, "c1"
		), 1L);

		assertEquals("SUBMITTED", result.status());
		assertEquals("ord-1", result.providerOrderId());
	}

	@Test
	void shouldRejectLimitOrderWithoutPrice() {
		DefaultTradeOrderService service = new DefaultTradeOrderService(
			tradeOrderRepository,
			tradeAccountRepository,
			tradeFillRepository,
			AesGcmCryptoUtil.fromBase64Key(BASE64_KEY),
			tradeProperties(),
			List.of()
		);

		assertThrows(IllegalArgumentException.class, () -> service.create(new TradeOrderCreateCommand(
			1L, "BTC-USDT", "BUY", "LIMIT", null, new BigDecimal("0.01"), null, "c1"
		), 1L));
	}

	@Test
	void shouldRefreshOrderAndSyncFill() {
		AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
		TradeAccountEntity account = account(cryptoUtil);
		ReflectionTestUtils.setField(account, "id", 1L);

		TradeOrderEntity order = new TradeOrderEntity();
		ReflectionTestUtils.setField(order, "id", 11L);
		order.setAccountId(1L);
		order.setProvider("OKX");
		order.setClientOrderId("c1");
		order.setProviderOrderId("ord-1");
		order.setSymbol("BTC-USDT");
		order.setSide("BUY");
		order.setOrderType("MARKET");
		order.setMarketType("SPOT");
		order.setQuantity(new BigDecimal("0.01"));
		order.setStatus("SUBMITTED");
		order.setFilledQuantity(BigDecimal.ZERO);
		order.setFilledAmount(BigDecimal.ZERO);

		TradeOrderProvider provider = new TradeOrderProvider() {
			@Override
			public String provider() {
				return "OKX";
			}

			@Override
			public TradeProviderSubmitResult submit(TradeAccountEntity a, String apiSecret, String passphrase, TradeOrderCreateCommand c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public TradeProviderCancelResult cancel(TradeAccountEntity a, String apiSecret, String passphrase, TradeOrderEntity o) {
				throw new UnsupportedOperationException();
			}

			@Override
			public TradeProviderOrderState queryOrder(TradeAccountEntity a, String apiSecret, String passphrase, TradeOrderEntity o) {
				return new TradeProviderOrderState(true, "PARTIALLY_FILLED", "ord-1", new BigDecimal("100"), new BigDecimal("0.01"), new BigDecimal("1"), null, null);
			}

			@Override
			public List<TradeProviderFillState> listFills(TradeAccountEntity a, String apiSecret, String passphrase, TradeOrderEntity o) {
				return List.of(new TradeProviderFillState(
					"fill-1",
					"BTC-USDT",
					"BUY",
					new BigDecimal("100"),
					new BigDecimal("0.01"),
					new BigDecimal("-0.001"),
					"USDT",
					Instant.parse("2026-05-04T12:00:02Z")
				));
			}
		};

		DefaultTradeOrderService service = new DefaultTradeOrderService(
			tradeOrderRepository,
			tradeAccountRepository,
			tradeFillRepository,
			cryptoUtil,
			tradeProperties(),
			List.of(provider)
		);

		when(tradeOrderRepository.findById(11L)).thenReturn(Optional.of(order));
		when(tradeAccountRepository.findById(1L)).thenReturn(Optional.of(account));
		when(tradeOrderRepository.save(any(TradeOrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(tradeFillRepository.findByOrderIdAndProviderFillId(11L, "fill-1")).thenReturn(Optional.empty());
		when(tradeFillRepository.save(any(TradeFillEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var result = service.refresh(11L, 1L);

		assertEquals("PARTIALLY_FILLED", result.status());
		assertEquals(new BigDecimal("0.01"), result.filledQuantity());
		verify(tradeFillRepository).save(any(TradeFillEntity.class));
	}

	@Test
	void shouldListFills() {
		DefaultTradeOrderService service = new DefaultTradeOrderService(
			tradeOrderRepository,
			tradeAccountRepository,
			tradeFillRepository,
			AesGcmCryptoUtil.fromBase64Key(BASE64_KEY),
			tradeProperties(),
			List.of()
		);
		TradeFillEntity fill = new TradeFillEntity();
		ReflectionTestUtils.setField(fill, "id", 21L);
		fill.setOrderId(11L);
		fill.setProviderFillId("fill-1");
		fill.setSymbol("BTC-USDT");
		fill.setSide("BUY");
		fill.setPrice(new BigDecimal("100"));
		fill.setQuantity(new BigDecimal("0.01"));
		fill.setFee(new BigDecimal("-0.001"));
		fill.setFeeCurrency("USDT");
		fill.setFilledAt(Instant.parse("2026-05-04T12:00:02Z"));

		when(tradeOrderRepository.existsById(11L)).thenReturn(true);
		when(tradeFillRepository.findByOrderIdOrderByFilledAtAscIdAsc(11L)).thenReturn(List.of(fill));

		List<TradeFillView> result = service.listFills(11L);

		assertEquals(1, result.size());
		assertEquals("fill-1", result.get(0).providerFillId());
	}

	@Test
	void shouldRejectCreateWhenTradeDisabled() {
		TradeProperties properties = new TradeProperties();
		properties.setEnabled(false);
		DefaultTradeOrderService service = new DefaultTradeOrderService(
			tradeOrderRepository,
			tradeAccountRepository,
			tradeFillRepository,
			AesGcmCryptoUtil.fromBase64Key(BASE64_KEY),
			properties,
			List.of()
		);

		TradeRiskException ex = assertThrows(TradeRiskException.class, () -> service.create(new TradeOrderCreateCommand(
			1L, "BTC-USDT", "BUY", "MARKET", null, new BigDecimal("0.01"), null, "c1"
		), 1L));
		assertEquals(CoreErrorCode.TRADE_DISABLED, ex.getErrorCode());
	}

	@Test
	void shouldRejectLiveAccountWhenSandboxOnlyEnabled() {
		AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
		TradeAccountEntity account = account(cryptoUtil);
		ReflectionTestUtils.setField(account, "id", 1L);
		account.setEnvType("LIVE");
		DefaultTradeOrderService service = new DefaultTradeOrderService(
			tradeOrderRepository,
			tradeAccountRepository,
			tradeFillRepository,
			cryptoUtil,
			tradeProperties(),
			List.of()
		);
		when(tradeAccountRepository.findById(1L)).thenReturn(Optional.of(account));

		TradeRiskException ex = assertThrows(TradeRiskException.class, () -> service.create(new TradeOrderCreateCommand(
			1L, "BTC-USDT", "BUY", "MARKET", null, new BigDecimal("0.01"), null, "c1"
		), 1L));
		assertEquals(CoreErrorCode.TRADE_LIVE_DISABLED, ex.getErrorCode());
	}

	@Test
	void shouldRejectCreateWhenAccountSecretsMissing() {
		AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
		TradeAccountEntity account = account(cryptoUtil);
		ReflectionTestUtils.setField(account, "id", 1L);
		account.setApiSecretCipher(null);
		DefaultTradeOrderService service = new DefaultTradeOrderService(
			tradeOrderRepository,
			tradeAccountRepository,
			tradeFillRepository,
			cryptoUtil,
			tradeProperties(),
			List.of()
		);
		when(tradeAccountRepository.findById(1L)).thenReturn(Optional.of(account));

		TradeRiskException ex = assertThrows(TradeRiskException.class, () -> service.create(new TradeOrderCreateCommand(
			1L, "BTC-USDT", "BUY", "MARKET", null, new BigDecimal("0.01"), null, "c1"
		), 1L));
		assertEquals(CoreErrorCode.TRADE_ACCOUNT_INVALID, ex.getErrorCode());
	}

	@Test
	void shouldRejectCreateWhenAccountDisabled() {
		AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
		TradeAccountEntity account = account(cryptoUtil);
		ReflectionTestUtils.setField(account, "id", 1L);
		account.setEnabled(false);
		DefaultTradeOrderService service = new DefaultTradeOrderService(
			tradeOrderRepository,
			tradeAccountRepository,
			tradeFillRepository,
			cryptoUtil,
			tradeProperties(),
			List.of()
		);
		when(tradeAccountRepository.findById(1L)).thenReturn(Optional.of(account));

		TradeRiskException ex = assertThrows(TradeRiskException.class, () -> service.create(new TradeOrderCreateCommand(
			1L, "BTC-USDT", "BUY", "MARKET", null, new BigDecimal("0.01"), null, "c1"
		), 1L));
		assertEquals(CoreErrorCode.TRADE_ACCOUNT_DISABLED, ex.getErrorCode());
	}

	@Test
	void shouldRejectCreateWhenEnvTypeInvalid() {
		AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
		TradeAccountEntity account = account(cryptoUtil);
		ReflectionTestUtils.setField(account, "id", 1L);
		account.setEnvType("paper");
		DefaultTradeOrderService service = new DefaultTradeOrderService(
			tradeOrderRepository,
			tradeAccountRepository,
			tradeFillRepository,
			cryptoUtil,
			tradeProperties(),
			List.of()
		);
		when(tradeAccountRepository.findById(1L)).thenReturn(Optional.of(account));

		TradeRiskException ex = assertThrows(TradeRiskException.class, () -> service.create(new TradeOrderCreateCommand(
			1L, "BTC-USDT", "BUY", "MARKET", null, new BigDecimal("0.01"), null, "c1"
		), 1L));
		assertEquals(CoreErrorCode.TRADE_ACCOUNT_INVALID, ex.getErrorCode());
	}

	@Test
	void shouldRejectCreateWhenQuantityExceedsLimit() {
		AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
		TradeAccountEntity account = account(cryptoUtil);
		ReflectionTestUtils.setField(account, "id", 1L);
		TradeProperties properties = tradeProperties();
		properties.setMaxOrderQuantity(new BigDecimal("0.005"));
		DefaultTradeOrderService service = new DefaultTradeOrderService(
			tradeOrderRepository,
			tradeAccountRepository,
			tradeFillRepository,
			cryptoUtil,
			properties,
			List.of()
		);
		when(tradeAccountRepository.findById(1L)).thenReturn(Optional.of(account));

		TradeRiskException ex = assertThrows(TradeRiskException.class, () -> service.create(new TradeOrderCreateCommand(
			1L, "BTC-USDT", "BUY", "MARKET", null, new BigDecimal("0.01"), null, "c1"
		), 1L));
		assertEquals(CoreErrorCode.TRADE_RISK_REJECTED, ex.getErrorCode());
	}

	@Test
	void shouldRejectCreateWhenNotionalExceedsLimit() {
		AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
		TradeAccountEntity account = account(cryptoUtil);
		ReflectionTestUtils.setField(account, "id", 1L);
		TradeProperties properties = tradeProperties();
		properties.setMaxOrderNotional(new BigDecimal("500"));
		DefaultTradeOrderService service = new DefaultTradeOrderService(
			tradeOrderRepository,
			tradeAccountRepository,
			tradeFillRepository,
			cryptoUtil,
			properties,
			List.of()
		);
		when(tradeAccountRepository.findById(1L)).thenReturn(Optional.of(account));

		TradeRiskException ex = assertThrows(TradeRiskException.class, () -> service.create(new TradeOrderCreateCommand(
			1L, "BTC-USDT", "BUY", "LIMIT", new BigDecimal("60000"), new BigDecimal("0.01"), null, "c1"
		), 1L));
		assertEquals(CoreErrorCode.TRADE_RISK_REJECTED, ex.getErrorCode());
	}

	@Test
	void shouldRejectDuplicateClientOrderIdBeforeProviderSubmit() {
		AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
		TradeAccountEntity account = account(cryptoUtil);
		ReflectionTestUtils.setField(account, "id", 1L);
		TradeOrderProvider provider = mock(TradeOrderProvider.class);
		when(provider.provider()).thenReturn("OKX");
		DefaultTradeOrderService service = new DefaultTradeOrderService(
			tradeOrderRepository,
			tradeAccountRepository,
			tradeFillRepository,
			cryptoUtil,
			tradeProperties(),
			List.of(provider)
		);
		when(tradeAccountRepository.findById(1L)).thenReturn(Optional.of(account));
		when(tradeOrderRepository.findByClientOrderId("c1")).thenReturn(Optional.of(new TradeOrderEntity()));

		TradeRiskException ex = assertThrows(TradeRiskException.class, () -> service.create(new TradeOrderCreateCommand(
			1L, "BTC-USDT", "BUY", "MARKET", null, new BigDecimal("0.01"), null, "c1"
		), 1L));
		assertEquals(CoreErrorCode.TRADE_ORDER_DUPLICATE, ex.getErrorCode());
		verify(provider, never()).submit(any(), any(), any(), any());
	}

	private TradeAccountEntity account(AesGcmCryptoUtil cryptoUtil) {
		TradeAccountEntity entity = new TradeAccountEntity();
		entity.setName("okx-main");
		entity.setProvider("OKX");
		entity.setAccountType("API_KEY");
		entity.setEnvType("SIMULATED");
		entity.setApiKey("api-key");
		entity.setApiSecretCipher(cryptoUtil.encrypt("secret"));
		entity.setPassphraseCipher(cryptoUtil.encrypt("pass"));
		entity.setEnabled(true);
		return entity;
	}
}
