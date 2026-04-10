package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.config.PriceTickIngestProperties;
import com.chainsentinel.infra.entity.PriceTickEntity;
import com.chainsentinel.infra.repository.PriceTickRepository;
import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.stream.PriceStreamQuote;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DbPriceTickBatchWriterTest {

	@Mock
	private PriceTickRepository priceTickRepository;

	@Test
	void shouldDedupSameProviderInstTsInSingleBatch() {
		PriceTickIngestProperties properties = new PriceTickIngestProperties();
		properties.setEnabled(true);
		properties.setBatchSize(100);

		DbPriceTickBatchWriter writer = new DbPriceTickBatchWriter(
			priceTickRepository,
			properties,
			new SimpleMeterRegistry()
		);

		PriceStreamQuote first = new PriceStreamQuote(
			"okx_ws",
			"OFFCHAIN",
			PriceInstType.SPOT,
			"BTC",
			"USDT",
			new BigDecimal("70000.1"),
			1700000000000L
		);
		PriceStreamQuote duplicate = new PriceStreamQuote(
			"okx_ws",
			"OFFCHAIN",
			PriceInstType.SPOT,
			"BTC",
			"USDT",
			new BigDecimal("70000.2"),
			1700000000000L
		);
		writer.enqueue(first);
		writer.enqueue(duplicate);

		when(priceTickRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
		writer.flushNow();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<PriceTickEntity>> captor = ArgumentCaptor.forClass(List.class);
		verify(priceTickRepository, times(1)).saveAll(captor.capture());
		List<PriceTickEntity> saved = captor.getValue();
		assertEquals(1, saved.size());
		assertEquals(new BigDecimal("70000.1"), saved.get(0).getPrice());
	}

	@Test
	void shouldFallbackToSingleSaveWhenBatchSaveHasDuplicateKeyConflict() {
		PriceTickIngestProperties properties = new PriceTickIngestProperties();
		properties.setEnabled(true);
		properties.setBatchSize(100);

		DbPriceTickBatchWriter writer = new DbPriceTickBatchWriter(
			priceTickRepository,
			properties,
			new SimpleMeterRegistry()
		);

		PriceStreamQuote q1 = new PriceStreamQuote(
			"okx_ws",
			"OFFCHAIN",
			PriceInstType.SPOT,
			"BTC",
			"USDT",
			new BigDecimal("70000.1"),
			1700000000000L
		);
		PriceStreamQuote q2 = new PriceStreamQuote(
			"okx_ws",
			"OFFCHAIN",
			PriceInstType.SPOT,
			"ETH",
			"USDT",
			new BigDecimal("3000.1"),
			1700000001000L
		);
		writer.enqueue(q1);
		writer.enqueue(q2);

		when(priceTickRepository.saveAll(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
		AtomicInteger singleSaveCount = new AtomicInteger(0);
		when(priceTickRepository.save(any())).thenAnswer(invocation -> {
			int current = singleSaveCount.incrementAndGet();
			if (current == 2) {
				throw new DataIntegrityViolationException("duplicate on second row");
			}
			return invocation.getArgument(0);
		});

		assertDoesNotThrow(writer::flushNow);
		verify(priceTickRepository, times(1)).saveAll(any());
		verify(priceTickRepository, times(2)).save(any());
	}
}

