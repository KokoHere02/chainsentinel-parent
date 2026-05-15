package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.chainsentinel.price.api.PublicMarketDataClient;
import com.chainsentinel.price.api.dto.PriceHistoryCandle;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OkxPriceTickBackfillServiceConcurrencyTest {

	@Mock
	private PublicMarketDataClient marketDataClient;

	@Mock
	private PriceTickPersistenceService priceTickPersistenceService;

	@Test
	void shouldRejectConcurrentBackfillForSameInstId() throws Exception {
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		when(marketDataClient.getHistoryCandles(eq("BTC-USDT"), any(), anyLong(), anyInt())).thenAnswer(invocation -> {
			entered.countDown();
			if (!release.await(3, TimeUnit.SECONDS)) {
				throw new IllegalStateException("test timeout waiting release");
			}
			return List.<PriceHistoryCandle>of();
		});

		OkxPriceTickBackfillService service = new OkxPriceTickBackfillService(marketDataClient, priceTickPersistenceService);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> first = executor.submit(() -> service.backfill("BTC-USDT", 1L, 2L, "1m", 100, 1, 0));
			assertTrue(entered.await(2, TimeUnit.SECONDS));

			IllegalStateException ex = assertThrows(
				IllegalStateException.class,
				() -> service.backfill("btc-usdt", 1L, 2L, "1m", 100, 1, 0)
			);
			assertTrue(ex.getMessage().contains("BTC-USDT"));

			release.countDown();
			first.get(3, TimeUnit.SECONDS);
		} finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void shouldAllowConcurrentBackfillForDifferentInstId() throws Exception {
		CountDownLatch btcEntered = new CountDownLatch(1);
		CountDownLatch btcRelease = new CountDownLatch(1);

		when(marketDataClient.getHistoryCandles(any(), any(), anyLong(), anyInt())).thenAnswer(invocation -> {
			String instId = invocation.getArgument(0, String.class);
			if ("BTC-USDT".equals(instId)) {
				btcEntered.countDown();
				if (!btcRelease.await(3, TimeUnit.SECONDS)) {
					throw new IllegalStateException("test timeout waiting btc release");
				}
			}
			return List.<PriceHistoryCandle>of();
		});

		OkxPriceTickBackfillService service = new OkxPriceTickBackfillService(marketDataClient, priceTickPersistenceService);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<OkxPriceTickBackfillService.BackfillResult> first = executor.submit(
				() -> service.backfill("BTC-USDT", 1L, 2L, "1m", 100, 1, 0)
			);
			assertTrue(btcEntered.await(2, TimeUnit.SECONDS));

			OkxPriceTickBackfillService.BackfillResult second = service.backfill("ETH-USDT", 1L, 2L, "1m", 100, 1, 0);
			assertEquals("ETH-USDT", second.instId());

			btcRelease.countDown();
			first.get(3, TimeUnit.SECONDS);
		} finally {
			btcRelease.countDown();
			executor.shutdownNow();
		}
	}
}
