package com.chainsentinel.infra.job;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.config.PriceStreamProperties;
import com.chainsentinel.infra.entity.PricePullTargetEntity;
import com.chainsentinel.infra.repository.PricePullTargetRepository;
import com.chainsentinel.price.stream.PriceStreamManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceStreamSubscriptionJobTest {

	@Mock
	private PriceStreamManager priceStreamManager;

	@Mock
	private PricePullTargetRepository pricePullTargetRepository;

	@Test
	void shouldRefreshOnStartupWhenEnabledAndTargetsExist() {
		PriceStreamProperties properties = new PriceStreamProperties();
		properties.setEnabled(true);

		PricePullTargetEntity target = new PricePullTargetEntity();
		target.setEnabled(true);
		target.setInstType("SPOT");
		target.setInstId("BTC-USDT");
		target.setQuoteSymbol("USDT");

		when(pricePullTargetRepository.findByEnabledTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(target));

		PriceStreamSubscriptionJob job = new PriceStreamSubscriptionJob(
			priceStreamManager,
			pricePullTargetRepository,
			properties
		);

		job.warmupSubscriptionsOnStartup();

		verify(priceStreamManager, times(1)).refreshSubscriptions(anyList());
	}

	@Test
	void shouldSkipRefreshWhenDisabled() {
		PriceStreamProperties properties = new PriceStreamProperties();
		properties.setEnabled(false);

		PriceStreamSubscriptionJob job = new PriceStreamSubscriptionJob(
			priceStreamManager,
			pricePullTargetRepository,
			properties
		);

		job.warmupSubscriptionsOnStartup();

		verify(pricePullTargetRepository, never()).findByEnabledTrueOrderByPriorityAscIdAsc();
		verify(priceStreamManager, never()).refreshSubscriptions(anyList());
	}
}

