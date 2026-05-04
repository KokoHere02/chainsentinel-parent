package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.common.crypto.AesGcmCryptoUtil;
import com.chainsentinel.infra.entity.TradeFillEntity;
import com.chainsentinel.infra.entity.TradeAccountBalanceSnapshotEntity;
import com.chainsentinel.infra.entity.TradePositionSnapshotEntity;
import com.chainsentinel.infra.repository.TradeAccountBalanceSnapshotRepository;
import com.chainsentinel.infra.repository.TradeAccountRepository;
import com.chainsentinel.infra.repository.TradeFillRepository;
import com.chainsentinel.infra.repository.TradePositionSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultTradeAccountAssetServiceTest {

	private static final String BASE64_KEY = "E68oWUgtnhEu1d6sLWPdiw==";

	@Mock
	private TradeAccountRepository tradeAccountRepository;

	@Mock
	private TradeAccountBalanceSnapshotRepository tradeAccountBalanceSnapshotRepository;

	@Mock
	private TradePositionSnapshotRepository tradePositionSnapshotRepository;

	@Mock
	private TradeFillRepository tradeFillRepository;

	@Test
	void shouldCalculateAvgCostFromFillsWhenPersistingPositions() {
		DefaultTradeAccountAssetService service = new DefaultTradeAccountAssetService(
			tradeAccountRepository,
			tradeAccountBalanceSnapshotRepository,
			tradePositionSnapshotRepository,
			tradeFillRepository,
			AesGcmCryptoUtil.fromBase64Key(BASE64_KEY),
			new RestTemplateBuilder(),
			new ObjectMapper()
		);
		when(tradeAccountRepository.existsById(1L)).thenReturn(true);
		when(tradeAccountBalanceSnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(tradePositionSnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(tradeFillRepository.listByAccountIdOrderByFilledAtAscIdAsc(1L)).thenReturn(List.of(
			fill(1L, "BTC-USDT", "BUY", "1", "100", "2026-05-04T12:00:00Z"),
			fill(2L, "BTC-USDT", "BUY", "1", "200", "2026-05-04T12:01:00Z"),
			fill(3L, "BTC-USDT", "SELL", "1", "150", "2026-05-04T12:02:00Z")
		));

		var result = service.snapshotFromBalances(
			1L,
			List.of(new TradeAssetBalanceItem("BTC", new BigDecimal("1"), BigDecimal.ZERO, new BigDecimal("1"))),
			Instant.parse("2026-05-04T12:05:00Z")
		);

		assertEquals(1, result.positionCount());
		assertEquals(1, result.balanceCount());
		ArgumentCaptor<TradeAccountBalanceSnapshotEntity> balanceCaptor = ArgumentCaptor.forClass(TradeAccountBalanceSnapshotEntity.class);
		verify(tradeAccountBalanceSnapshotRepository).save(balanceCaptor.capture());
		assertEquals("HTTP", balanceCaptor.getValue().getSource());
		ArgumentCaptor<TradePositionSnapshotEntity> captor = ArgumentCaptor.forClass(TradePositionSnapshotEntity.class);
		verify(tradePositionSnapshotRepository).save(captor.capture());
		assertEquals(new BigDecimal("150.000000000000000000"), captor.getValue().getAvgCost());
		assertEquals(null, captor.getValue().getMarketPrice());
		assertEquals("HTTP", captor.getValue().getSource());
	}

	@Test
	void shouldCalculateUnrealizedPnlWhenMarketPricePresent() {
		DefaultTradeAccountAssetService service = new DefaultTradeAccountAssetService(
			tradeAccountRepository,
			tradeAccountBalanceSnapshotRepository,
			tradePositionSnapshotRepository,
			tradeFillRepository,
			AesGcmCryptoUtil.fromBase64Key(BASE64_KEY),
			new RestTemplateBuilder(),
			new ObjectMapper()
		) {
			@Override
			protected BigDecimal fetchTickerPrice(String instId) {
				return new BigDecimal("180");
			}
		};
		when(tradeAccountRepository.existsById(1L)).thenReturn(true);
		when(tradeAccountBalanceSnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(tradePositionSnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(tradeFillRepository.listByAccountIdOrderByFilledAtAscIdAsc(1L)).thenReturn(List.of(
			fill(1L, "BTC-USDT", "BUY", "1", "150", "2026-05-04T12:00:00Z")
		));

		service.snapshotFromBalances(
			1L,
			List.of(new TradeAssetBalanceItem("BTC", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE)),
			Instant.parse("2026-05-04T12:05:00Z")
		);

		ArgumentCaptor<TradePositionSnapshotEntity> captor = ArgumentCaptor.forClass(TradePositionSnapshotEntity.class);
		verify(tradePositionSnapshotRepository).save(captor.capture());
		assertEquals(new BigDecimal("30.000000000000000000"), captor.getValue().getUnrealizedPnl());
		assertEquals(new BigDecimal("0.200000000000000000"), captor.getValue().getUnrealizedPnlRatio());
	}

	@Test
	void shouldSkipPersistWhenSnapshotUnchanged() {
		DefaultTradeAccountAssetService service = new DefaultTradeAccountAssetService(
			tradeAccountRepository,
			tradeAccountBalanceSnapshotRepository,
			tradePositionSnapshotRepository,
			tradeFillRepository,
			AesGcmCryptoUtil.fromBase64Key(BASE64_KEY),
			new RestTemplateBuilder(),
			new ObjectMapper()
		) {
			@Override
			protected BigDecimal fetchTickerPrice(String instId) {
				return new BigDecimal("180");
			}
		};
		when(tradeAccountRepository.existsById(1L)).thenReturn(true);
		when(tradeAccountBalanceSnapshotRepository.findLatestSnapshotTimeByAccountId(1L))
			.thenReturn(Instant.parse("2026-05-04T12:00:00Z"));
		when(tradePositionSnapshotRepository.findLatestSnapshotTimeByAccountId(1L))
			.thenReturn(Instant.parse("2026-05-04T12:00:00Z"));
		when(tradeAccountBalanceSnapshotRepository.findByAccountIdAndSnapshotTimeOrderByAssetAsc(1L, Instant.parse("2026-05-04T12:00:00Z")))
			.thenReturn(List.of(balance(1L, "BTC", "1", "0", "1", "HTTP", "2026-05-04T12:00:00Z")));
		when(tradePositionSnapshotRepository.findByAccountIdAndSnapshotTimeOrderBySymbolAsc(1L, Instant.parse("2026-05-04T12:00:00Z")))
			.thenReturn(List.of(position(1L, "BTC-USDT", "BTC", "USDT", "1", "150", "180", "180", "30", "0.2", "HTTP", "2026-05-04T12:00:00Z")));
		when(tradeFillRepository.listByAccountIdOrderByFilledAtAscIdAsc(1L)).thenReturn(List.of(
			fill(1L, "BTC-USDT", "BUY", "1", "150", "2026-05-04T12:00:00Z")
		));

		var result = service.snapshotFromBalances(
			1L,
			List.of(new TradeAssetBalanceItem("BTC", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE)),
			Instant.parse("2026-05-04T12:05:00Z")
		);

		assertEquals(0, result.balanceCount());
		assertEquals(0, result.positionCount());
		verify(tradeAccountBalanceSnapshotRepository, never()).save(any());
		verify(tradePositionSnapshotRepository, never()).save(any());
	}

	@Test
	void shouldIncludeQuoteFeeIntoAvgCost() {
		DefaultTradeAccountAssetService service = new DefaultTradeAccountAssetService(
			tradeAccountRepository,
			tradeAccountBalanceSnapshotRepository,
			tradePositionSnapshotRepository,
			tradeFillRepository,
			AesGcmCryptoUtil.fromBase64Key(BASE64_KEY),
			new RestTemplateBuilder(),
			new ObjectMapper()
		);
		when(tradeAccountRepository.existsById(1L)).thenReturn(true);
		when(tradeAccountBalanceSnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(tradePositionSnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(tradeFillRepository.listByAccountIdOrderByFilledAtAscIdAsc(1L)).thenReturn(List.of(
			fill(1L, "BTC-USDT", "BUY", "1", "100", "USDT", "2", "2026-05-04T12:00:00Z")
		));

		service.snapshotFromBalances(
			1L,
			List.of(new TradeAssetBalanceItem("BTC", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE)),
			Instant.parse("2026-05-04T12:05:00Z")
		);

		ArgumentCaptor<TradePositionSnapshotEntity> captor = ArgumentCaptor.forClass(TradePositionSnapshotEntity.class);
		verify(tradePositionSnapshotRepository).save(captor.capture());
		assertEquals(new BigDecimal("102.000000000000000000"), captor.getValue().getAvgCost());
	}

	private TradeFillEntity fill(Long id, String symbol, String side, String quantity, String price, String filledAt) {
		return fill(id, symbol, side, quantity, price, null, null, filledAt);
	}

	private TradeFillEntity fill(Long id, String symbol, String side, String quantity, String price, String feeCurrency, String fee, String filledAt) {
		TradeFillEntity entity = new TradeFillEntity();
		ReflectionTestUtils.setField(entity, "id", id);
		entity.setSymbol(symbol);
		entity.setSide(side);
		entity.setQuantity(new BigDecimal(quantity));
		entity.setPrice(new BigDecimal(price));
		entity.setFeeCurrency(feeCurrency);
		entity.setFee(fee == null ? null : new BigDecimal(fee));
		entity.setFilledAt(Instant.parse(filledAt));
		return entity;
	}

	private TradeAccountBalanceSnapshotEntity balance(Long id, String asset, String available, String frozen, String total, String source, String snapshotTime) {
		TradeAccountBalanceSnapshotEntity entity = new TradeAccountBalanceSnapshotEntity();
		ReflectionTestUtils.setField(entity, "id", id);
		entity.setAccountId(1L);
		entity.setAsset(asset);
		entity.setAvailable(new BigDecimal(available));
		entity.setFrozen(new BigDecimal(frozen));
		entity.setTotal(new BigDecimal(total));
		entity.setSource(source);
		entity.setSnapshotTime(Instant.parse(snapshotTime));
		return entity;
	}

	private TradePositionSnapshotEntity position(
		Long id,
		String symbol,
		String baseAsset,
		String quoteAsset,
		String quantity,
		String avgCost,
		String marketPrice,
		String marketValue,
		String unrealizedPnl,
		String unrealizedPnlRatio,
		String source,
		String snapshotTime
	) {
		TradePositionSnapshotEntity entity = new TradePositionSnapshotEntity();
		ReflectionTestUtils.setField(entity, "id", id);
		entity.setAccountId(1L);
		entity.setSymbol(symbol);
		entity.setBaseAsset(baseAsset);
		entity.setQuoteAsset(quoteAsset);
		entity.setQuantity(new BigDecimal(quantity));
		entity.setAvgCost(new BigDecimal(avgCost));
		entity.setMarketPrice(new BigDecimal(marketPrice));
		entity.setMarketValue(new BigDecimal(marketValue));
		entity.setUnrealizedPnl(new BigDecimal(unrealizedPnl));
		entity.setUnrealizedPnlRatio(new BigDecimal(unrealizedPnlRatio));
		entity.setSource(source);
		entity.setSnapshotTime(Instant.parse(snapshotTime));
		return entity;
	}
}
