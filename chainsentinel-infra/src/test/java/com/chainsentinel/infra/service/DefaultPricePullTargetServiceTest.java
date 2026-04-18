package com.chainsentinel.infra.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.dto.PricePullTargetCreateCommand;
import com.chainsentinel.infra.entity.PriceProviderConfigEntity;
import com.chainsentinel.infra.entity.PricePullTargetEntity;
import com.chainsentinel.infra.repository.PriceProviderConfigRepository;
import com.chainsentinel.infra.repository.PricePullTargetRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultPricePullTargetServiceTest {

	@Mock
	private PricePullTargetRepository pricePullTargetRepository;

	@Mock
	private PriceProviderConfigRepository priceProviderConfigRepository;

	@Mock
	private PriceTickBackfillDispatchService backfillDispatchService;

	@InjectMocks
	private DefaultPricePullTargetService service;

	@Test
	void shouldSubmitAsyncBackfillWhenCreateEnabledOkxTarget() {
		PricePullTargetCreateCommand command = new PricePullTargetCreateCommand(
			1L,
			11L,
			"spot",
			"btc-usdt",
			"usdt",
			true,
			1000,
			1
		);
		PriceProviderConfigEntity provider = new PriceProviderConfigEntity();
		provider.setProviderName("okx");

		when(priceProviderConfigRepository.existsById(11L)).thenReturn(true);
		when(pricePullTargetRepository.save(any(PricePullTargetEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(priceProviderConfigRepository.findById(11L)).thenReturn(Optional.of(provider));

		service.create(command);

		verify(backfillDispatchService, times(1)).submitLast30Days(eq("BTC-USDT"), eq("target_create"));
	}
}