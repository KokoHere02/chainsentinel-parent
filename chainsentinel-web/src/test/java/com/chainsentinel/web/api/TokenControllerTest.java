package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.MonitorTokenService;
import com.chainsentinel.core.service.dto.MonitorTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorTokenView;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TokenControllerTest {

	@Mock
	private MonitorTokenService monitorTokenService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		TokenController controller = new TokenController(monitorTokenService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldUpsertTokenAndApplyDefaultEnabled() throws Exception {
		when(monitorTokenService.upsert(any(MonitorTokenUpsertCommand.class)))
			.thenReturn(new MonitorTokenView(1L, "ETH", "0xabc", "LINK", true));

		mockMvc.perform(post("/api/tokens")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  \"chain\": \"ETH\",
					  \"tokenContract\": \"0xAbC\",
					  \"symbol\": \"LINK\"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id", is(1)))
			.andExpect(jsonPath("$.chain", is("ETH")))
			.andExpect(jsonPath("$.tokenContract", is("0xabc")))
			.andExpect(jsonPath("$.enabled", is(true)));

		ArgumentCaptor<MonitorTokenUpsertCommand> captor = ArgumentCaptor.forClass(MonitorTokenUpsertCommand.class);
		verify(monitorTokenService).upsert(captor.capture());
		MonitorTokenUpsertCommand cmd = captor.getValue();
		Assertions.assertEquals("ETH", cmd.chain());
		Assertions.assertEquals("0xAbC", cmd.tokenContract());
		Assertions.assertEquals("LINK", cmd.symbol());
		Assertions.assertTrue(cmd.enabled());
	}

	@Test
	void shouldListTokens() throws Exception {
		when(monitorTokenService.list(eq("ETH"), eq("usdt"), eq(true), eq(30)))
			.thenReturn(List.of(
				new MonitorTokenView(1L, "ETH", "0xabc", "USDT", true),
				new MonitorTokenView(2L, "ETH", "0xdef", "USDC", true)
			));

		mockMvc.perform(get("/api/tokens")
				.param("chain", "ETH")
				.param("q", "usdt")
				.param("enabled", "true")
				.param("limit", "30"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].symbol", is("USDT")));

		verify(monitorTokenService).list("ETH", "usdt", true, 30);
	}

	@Test
	void shouldReturnBadRequestWhenLimitInvalid() throws Exception {
		mockMvc.perform(get("/api/tokens").param("limit", "0"))
			.andExpect(status().isBadRequest());
	}
}