package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.config.PriceTickBackfillProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceTickBackfillDispatchServiceTest {

	@Mock
	private OkxPriceTickBackfillService okxPriceTickBackfillService;

	@Test
	void shouldRecordSubmittedAndSuccessMetrics() {
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		PriceTickBackfillDispatchService service = new PriceTickBackfillDispatchService(
			okxPriceTickBackfillService,
			Runnable::run,
			meterRegistry,
			defaultProperties()
		);
		when(okxPriceTickBackfillService.backfill(
			eq("BTC-USDT"),
			anyLong(),
			anyLong(),
			eq("1m"),
			anyInt(),
			anyInt(),
			anyLong()
		)).thenReturn(new OkxPriceTickBackfillService.BackfillResult(
			"BTC-USDT",
			0L,
			0L,
			"1m",
			1,
			0,
			0,
			false,
			"ok",
			null,
			null,
			null,
			Instant.now(),
			Instant.now()
		));

		service.submitLast30Days("btc-usdt", "target_create");

		assertEquals(1.0,
			meterRegistry.get("price_tick_backfill_dispatch_total")
				.tags("trigger", "target_create", "status", "submitted")
				.counter().count());
		assertEquals(1.0,
			meterRegistry.get("price_tick_backfill_dispatch_total")
				.tags("trigger", "target_create", "status", "success")
				.counter().count());
		assertEquals(1L,
			meterRegistry.get("price_tick_backfill_duration")
				.tags("trigger", "target_create", "status", "success")
				.timer().count());
	}

	@Test
	void shouldSkipPendingWhenSameInstQueued() {
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		QueueingExecutor queueingExecutor = new QueueingExecutor();
		PriceTickBackfillDispatchService service = new PriceTickBackfillDispatchService(
			okxPriceTickBackfillService,
			queueingExecutor,
			meterRegistry,
			defaultProperties()
		);
		when(okxPriceTickBackfillService.backfill(
			eq("ETH-USDT"),
			anyLong(),
			anyLong(),
			eq("1m"),
			anyInt(),
			anyInt(),
			anyLong()
		)).thenReturn(new OkxPriceTickBackfillService.BackfillResult(
			"ETH-USDT",
			0L,
			0L,
			"1m",
			1,
			0,
			0,
			false,
			"ok",
			null,
			null,
			null,
			Instant.now(),
			Instant.now()
		));

		service.submitLast30Days("ETH-USDT", "daily");
		service.submitLast30Days("eth-usdt", "daily");

		assertEquals(1.0,
			meterRegistry.get("price_tick_backfill_dispatch_total")
				.tags("trigger", "daily", "status", "skipped_pending")
				.counter().count());

		queueingExecutor.runAll();

		verify(okxPriceTickBackfillService, times(1)).backfill(
			eq("ETH-USDT"),
			anyLong(),
			anyLong(),
			eq("1m"),
			anyInt(),
			anyInt(),
			anyLong()
		);
	}

	@Test
	void shouldRecordInvalidInstMetricWhenInstBlank() {
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		PriceTickBackfillDispatchService service = new PriceTickBackfillDispatchService(
			okxPriceTickBackfillService,
			Runnable::run,
			meterRegistry,
			defaultProperties()
		);

		service.submitLast30Days("   ", "daily");

		assertEquals(1.0,
			meterRegistry.get("price_tick_backfill_dispatch_total")
				.tags("trigger", "daily", "status", "skipped_invalid_inst")
				.counter().count());
	}

	@Test
	void shouldUseConfiguredBackfillParams() {
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		PriceTickBackfillProperties properties = new PriceTickBackfillProperties();
		properties.setRetentionDays(7);
		properties.setBar("5m");
		properties.setPageLimit(111);
		properties.setMaxRounds(222);
		properties.setSleepMs(333L);
		PriceTickBackfillDispatchService service = new PriceTickBackfillDispatchService(
			okxPriceTickBackfillService,
			Runnable::run,
			meterRegistry,
			properties
		);
		when(okxPriceTickBackfillService.backfill(
			eq("SOL-USDT"),
			anyLong(),
			anyLong(),
			eq("5m"),
			eq(111),
			eq(222),
			eq(333L)
		)).thenReturn(new OkxPriceTickBackfillService.BackfillResult(
			"SOL-USDT",
			0L,
			0L,
			"5m",
			1,
			0,
			0,
			false,
			"ok",
			null,
			null,
			null,
			Instant.now(),
			Instant.now()
		));

		service.submitLast30Days("SOL-USDT", "target_update");

		verify(okxPriceTickBackfillService, times(1)).backfill(
			eq("SOL-USDT"),
			anyLong(),
			anyLong(),
			eq("5m"),
			eq(111),
			eq(222),
			eq(333L)
		);
	}

	private PriceTickBackfillProperties defaultProperties() {
		return new PriceTickBackfillProperties();
	}

	private static class QueueingExecutor implements Executor {
		private final Queue<Runnable> tasks = new ArrayDeque<>();

		@Override
		public void execute(Runnable command) {
			tasks.add(command);
		}

		void runAll() {
			while (!tasks.isEmpty()) {
				tasks.poll().run();
			}
		}
	}
}
