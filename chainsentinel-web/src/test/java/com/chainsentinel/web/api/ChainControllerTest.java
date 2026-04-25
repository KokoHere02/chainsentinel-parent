package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.ChainConfigService;
import com.chainsentinel.core.service.dto.ChainConfigView;
import com.chainsentinel.web.api.support.GlobalExceptionHandler;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ChainControllerTest {

	@Mock
	private ChainConfigService chainConfigService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		ChainController controller = new ChainController(chainConfigService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldDeleteChainConfig() throws Exception {
		when(chainConfigService.delete("ETH", "mainnet")).thenReturn(true);

		mockMvc.perform(delete("/api/chains/ETH/mainnet"))
			.andExpect(status().isNoContent());

		verify(chainConfigService).delete("ETH", "mainnet");
	}

	@Test
	void shouldReturnNotFoundWhenDeleteMissingChainConfig() throws Exception {
		when(chainConfigService.delete("ETH", "mainnet")).thenReturn(false);

		mockMvc.perform(delete("/api/chains/ETH/mainnet"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code", is("NOT_FOUND")));
	}

	@Test
	void shouldEnableChainConfig() throws Exception {
		when(chainConfigService.setEnabled(eq("ETH"), eq("mainnet"), eq(true)))
			.thenReturn(Optional.of(new ChainConfigView(
				1L, "ETH", "mainnet", "https://rpc", "https://rpc", "wss://rpc", "HTTP", 12, true
			)));

		mockMvc.perform(patch("/api/chains/ETH/mainnet/enable"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled", is(true)))
			.andExpect(jsonPath("$.chain", is("ETH")));
	}

	@Test
	void shouldDisableChainConfig() throws Exception {
		when(chainConfigService.setEnabled(eq("ETH"), eq("mainnet"), eq(false)))
			.thenReturn(Optional.of(new ChainConfigView(
				1L, "ETH", "mainnet", "https://rpc", "https://rpc", "wss://rpc", "HTTP", 12, false
			)));

		mockMvc.perform(patch("/api/chains/ETH/mainnet/disable"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled", is(false)));
	}

	@Test
	void shouldReturnNotFoundWhenEnableMissingChainConfig() throws Exception {
		when(chainConfigService.setEnabled(eq("ETH"), eq("mainnet"), eq(true))).thenReturn(Optional.empty());

		mockMvc.perform(patch("/api/chains/ETH/mainnet/enable"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code", is("NOT_FOUND")));
	}
}
