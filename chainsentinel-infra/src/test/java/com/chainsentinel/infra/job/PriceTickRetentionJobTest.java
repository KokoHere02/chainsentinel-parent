package com.chainsentinel.infra.job;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.config.PriceTickRetentionProperties;
import com.chainsentinel.infra.repository.PriceTickRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceTickRetentionJobTest {

	@Mock
	private PriceTickRepository priceTickRepository;

	@Test
	void shouldSkipWhenDisabled() {
		PriceTickRetentionProperties properties = new PriceTickRetentionProperties();
		properties.setEnabled(false);

		PriceTickRetentionJob job = new PriceTickRetentionJob(
			priceTickRepository,
			properties,
			new SimpleMeterRegistry()
		);
		job.run();

		verify(priceTickRepository, never()).deleteByQuoteTsBefore(anyLong());
	}

	@Test
	void shouldDeleteExpiredTicksWhenEnabled() {
		PriceTickRetentionProperties properties = new PriceTickRetentionProperties();
		properties.setEnabled(true);
		properties.setRetentionDays(7);

		when(priceTickRepository.deleteByQuoteTsBefore(anyLong())).thenReturn(12);

		PriceTickRetentionJob job = new PriceTickRetentionJob(
			priceTickRepository,
			properties,
			new SimpleMeterRegistry()
		);
		job.run();

		verify(priceTickRepository, times(1)).deleteByQuoteTsBefore(anyLong());
	}
}

