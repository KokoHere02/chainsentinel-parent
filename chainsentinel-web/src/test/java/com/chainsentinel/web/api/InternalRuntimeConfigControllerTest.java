package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.infra.job.PriceStreamSubscriptionJob;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InternalRuntimeConfigControllerTest {

	@Mock
	private PriceProviderRuntimeConfig priceProviderRuntimeConfig;

	@Mock
	private PriceStreamSubscriptionJob priceStreamSubscriptionJob;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		InternalRuntimeConfigController controller = new InternalRuntimeConfigController(
			priceProviderRuntimeConfig,
			priceStreamSubscriptionJob
		);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldRefreshPriceRuntimeConfigCache() throws Exception {
		mockMvc.perform(post("/api/internal/runtime-config/price/refresh"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.refreshed", is(true)))
			.andExpect(jsonPath("$.refreshedAt").exists());

		verify(priceProviderRuntimeConfig).refreshCache();
	}

	@Test
	void shouldRefreshPriceWsSubscriptions() throws Exception {
		mockMvc.perform(post("/api/internal/runtime-config/price-ws/refresh"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.refreshed", is(true)))
			.andExpect(jsonPath("$.refreshedAt").exists());

		verify(priceStreamSubscriptionJob).refreshSubscriptions();
	}
}
