package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.repository.PriceTickRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceTickQueryServiceTest {

	@Mock
	private PriceTickRepository priceTickRepository;

	private PriceTickQueryService service;

	@BeforeEach
	void setUp() {
		service = new PriceTickQueryService(priceTickRepository);
	}

	@Test
	void shouldAggregateUsingDatabaseBuckets() {
		PriceTickRepository.PriceTickAggregateRow row1 = new StubRow(
			172800000L,
			new BigDecimal("70100.1"),
			new BigDecimal("70000.1"),
			new BigDecimal("70200.1"),
			12L
		);
		PriceTickRepository.PriceTickAggregateRow row2 = new StubRow(
			86400000L,
			new BigDecimal("69000.1"),
			new BigDecimal("68000.1"),
			new BigDecimal("69500.1"),
			34L
		);
		when(priceTickRepository.queryTickAggregatesByProviderAndInst(
			eq("okx_ws"),
			eq("BTC-USDT"),
			eq(0L),
			eq(259200000L),
			eq(86400000L),
			eq(2)
		)).thenReturn(List.of(row1, row2));

		List<PriceTickQueryService.PriceTickAggregateView> result = service.aggregate(
			"okx_ws",
			"btc-usdt",
			0L,
			259200000L,
			86400000L,
			2
		);

		assertEquals(2, result.size());
		assertEquals(172800000L, result.get(0).bucketStartTs());
		assertEquals(new BigDecimal("70100.1"), result.get(0).last());
		assertEquals(12L, result.get(0).count());
		assertEquals(86400000L, result.get(1).bucketStartTs());
		assertEquals(new BigDecimal("68000.1"), result.get(1).min());
		assertEquals(new BigDecimal("69500.1"), result.get(1).max());
	}

	private static class StubRow implements PriceTickRepository.PriceTickAggregateRow {
		private final Long bucketStartTs;
		private final BigDecimal lastPrice;
		private final BigDecimal minPrice;
		private final BigDecimal maxPrice;
		private final Long count;

		private StubRow(Long bucketStartTs, BigDecimal lastPrice, BigDecimal minPrice, BigDecimal maxPrice, Long count) {
			this.bucketStartTs = bucketStartTs;
			this.lastPrice = lastPrice;
			this.minPrice = minPrice;
			this.maxPrice = maxPrice;
			this.count = count;
		}

		@Override
		public Long getBucketStartTs() {
			return bucketStartTs;
		}

		@Override
		public BigDecimal getLastPrice() {
			return lastPrice;
		}

		@Override
		public BigDecimal getMinPrice() {
			return minPrice;
		}

		@Override
		public BigDecimal getMaxPrice() {
			return maxPrice;
		}

		@Override
		public Long getCount() {
			return count;
		}
	}
}
