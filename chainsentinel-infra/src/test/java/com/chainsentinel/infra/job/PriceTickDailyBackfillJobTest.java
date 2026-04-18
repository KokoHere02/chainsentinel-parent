package com.chainsentinel.infra.job;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.entity.PricePullTargetEntity;
import com.chainsentinel.infra.repository.PricePullTargetRepository;
import com.chainsentinel.infra.service.PriceTickBackfillDispatchService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceTickDailyBackfillJobTest {

	@Mock
	private PricePullTargetRepository pricePullTargetRepository;

	@Mock
	private PriceTickBackfillDispatchService backfillDispatchService;

	@InjectMocks
	private PriceTickDailyBackfillJob job;

	@Test
	void shouldSubmitOncePerUniqueInstId() {
		PricePullTargetEntity t1 = target("BTC-USDT");
		PricePullTargetEntity t2 = target("btc-usdt");
		PricePullTargetEntity t3 = target(" ETH-USDT ");
		PricePullTargetEntity t4 = target(" ");

		when(pricePullTargetRepository.findEnabledByProviderName("okx"))
			.thenReturn(Arrays.asList(t1, t2, t3, t4, null));

		job.runDaily();

		verify(backfillDispatchService, times(1)).submitLast30Days(eq("BTC-USDT"), eq("daily"));
		verify(backfillDispatchService, times(1)).submitLast30Days(eq("ETH-USDT"), eq("daily"));
		verifyNoMoreInteractions(backfillDispatchService);
	}

	private PricePullTargetEntity target(String instId) {
		PricePullTargetEntity entity = new PricePullTargetEntity();
		entity.setInstId(instId);
		return entity;
	}
}