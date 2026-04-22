package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.PriceProviderConfigService;
import com.chainsentinel.core.service.dto.PriceProviderConfigView;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PriceProviderConfigControllerTest {

	@Mock
	private PriceProviderConfigService priceProviderConfigService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		PriceProviderConfigController controller = new PriceProviderConfigController(priceProviderConfigService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldCreate() throws Exception {
		when(priceProviderConfigService.create(any())).thenReturn(new PriceProviderConfigView(
			1L, "okx", "https://www.okx.com", true, 1, 1500
		));

		mockMvc.perform(post("/api/price-provider-configs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  \"providerName\": \"okx\",
					  \"baseUrl\": \"https://www.okx.com\",
					  \"enabled\": true,
					  \"priority\": 1,
					  \"timeoutMs\": 1500
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id", is(1)))
			.andExpect(jsonPath("$.providerName", is("okx")));
	}

	@Test
	void shouldReturnBadRequestWhenDuplicateProvider() throws Exception {
		when(priceProviderConfigService.create(any())).thenThrow(
			new IllegalArgumentException("price provider config already exists: providerName=okx")
		);

		mockMvc.perform(post("/api/price-provider-configs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  \"providerName\": \"okx\",
					  \"baseUrl\": \"https://www.okx.com\",
					  \"enabled\": true,
					  \"priority\": 1,
					  \"timeoutMs\": 1500
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void shouldList() throws Exception {
		when(priceProviderConfigService.list(true, "okx", 20)).thenReturn(List.of(
			new PriceProviderConfigView(1L, "okx", "https://www.okx.com", true, 1, 1500)
		));

		mockMvc.perform(get("/api/price-provider-configs")
				.param("enabled", "true")
				.param("q", "okx")
				.param("limit", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].providerName", is("okx")));
	}

	@Test
	void shouldEnable() throws Exception {
		when(priceProviderConfigService.setEnabled(1L, true)).thenReturn(
			new PriceProviderConfigView(1L, "okx", "https://www.okx.com", true, 1, 1500)
		);

		mockMvc.perform(patch("/api/price-provider-configs/1/enable"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled", is(true)));
	}

	@Test
	void shouldDisable() throws Exception {
		when(priceProviderConfigService.setEnabled(1L, false)).thenReturn(
			new PriceProviderConfigView(1L, "okx", "https://www.okx.com", false, 1, 1500)
		);

		mockMvc.perform(patch("/api/price-provider-configs/1/disable"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled", is(false)));
	}

	@Test
	void shouldReturnNotFoundWhenEnableMissing() throws Exception {
		when(priceProviderConfigService.setEnabled(999L, true)).thenThrow(
			new NoSuchElementException("price provider config not found: 999")
		);

		mockMvc.perform(patch("/api/price-provider-configs/999/enable"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldDelete() throws Exception {
		mockMvc.perform(delete("/api/price-provider-configs/1"))
			.andExpect(status().isOk());
		verify(priceProviderConfigService).delete(1L);
	}
}
