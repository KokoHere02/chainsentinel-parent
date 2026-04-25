package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.dto.PriceProviderConfigCreateCommand;
import com.chainsentinel.infra.entity.PriceProviderConfigEntity;
import com.chainsentinel.infra.repository.PriceProviderConfigRepository;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultPriceProviderConfigServiceTest {

	@Mock
	private PriceProviderConfigRepository priceProviderConfigRepository;

	@Mock
	private PriceProviderRuntimeConfig priceProviderRuntimeConfig;

	@InjectMocks
	private DefaultPriceProviderConfigService service;

	@Test
	void shouldCreateAndRefreshRuntimeCache() {
		PriceProviderConfigCreateCommand command = new PriceProviderConfigCreateCommand(
			" OKX ",
			" https://www.okx.com ",
			true,
			1,
			1500
		);

		when(priceProviderConfigRepository.save(any(PriceProviderConfigEntity.class))).thenAnswer(invocation -> {
			PriceProviderConfigEntity entity = invocation.getArgument(0);
			entity.setEnabled(true);
			return entity;
		});

		var result = service.create(command);

		assertEquals("okx", result.providerName());
		assertEquals("https://www.okx.com", result.baseUrl());
		verify(priceProviderRuntimeConfig, times(1)).refreshCache();
	}

	@Test
	void shouldSetEnabledAndRefreshRuntimeCache() {
		PriceProviderConfigEntity entity = new PriceProviderConfigEntity();
		entity.setProviderName("okx");
		entity.setBaseUrl("https://www.okx.com");
		entity.setEnabled(true);
		entity.setPriority(1);
		entity.setTimeoutMs(1500);

		when(priceProviderConfigRepository.findById(1L)).thenReturn(Optional.of(entity));
		when(priceProviderConfigRepository.save(any(PriceProviderConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var result = service.setEnabled(1L, false);

		assertEquals(false, result.enabled());
		verify(priceProviderRuntimeConfig, times(1)).refreshCache();
	}

	@Test
	void shouldAllowWebSocketBaseUrl() {
		PriceProviderConfigCreateCommand command = new PriceProviderConfigCreateCommand(
			" OKX_WS ",
			" wss://ws.okx.com:8443/ws/v5/public ",
			true,
			1,
			1500
		);

		when(priceProviderConfigRepository.save(any(PriceProviderConfigEntity.class))).thenAnswer(invocation -> {
			PriceProviderConfigEntity entity = invocation.getArgument(0);
			entity.setEnabled(true);
			return entity;
		});

		var result = service.create(command);
		assertEquals("wss://ws.okx.com:8443/ws/v5/public", result.baseUrl());
	}

	@Test
	void shouldRejectUnsupportedBaseUrlScheme() {
		PriceProviderConfigCreateCommand command = new PriceProviderConfigCreateCommand(
			"okx",
			"ftp://www.okx.com",
			true,
			1,
			1500
		);
		assertThrows(IllegalArgumentException.class, () -> service.create(command));
	}
}
