package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.MonitorScopeTokenService;
import com.chainsentinel.core.service.dto.MonitorScopeTokenView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ScopeTokenControllerTest {

	@Mock
	private MonitorScopeTokenService monitorScopeTokenService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		ScopeTokenController controller = new ScopeTokenController(monitorScopeTokenService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldListScopeTokens() throws Exception {
		when(monitorScopeTokenService.list(eq(1L), eq("eth"), eq(true), eq(20)))
			.thenReturn(List.of(
				new MonitorScopeTokenView(1L, 1L, "NATIVE", "ETH", 18, true),
				new MonitorScopeTokenView(2L, 1L, "0xdac17f958d2ee523a2206206994597c13d831ec7", "USDT", 6, true)
			));

		mockMvc.perform(get("/api/scope-tokens")
				.param("monitorScopeId", "1")
				.param("q", "eth")
				.param("enabled", "true")
				.param("limit", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].tokenContract", is("NATIVE")));

		verify(monitorScopeTokenService).list(1L, "eth", true, 20);
	}

	@Test
	void shouldReturnBadRequestWhenLimitInvalid() throws Exception {
		mockMvc.perform(get("/api/scope-tokens").param("limit", "0"))
			.andExpect(status().isBadRequest());
	}
}

