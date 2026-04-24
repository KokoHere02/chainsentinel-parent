package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.MonitorTreeQueryService;
import com.chainsentinel.core.service.dto.MonitorAddressTreeView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MonitorTreeControllerTest {

	@Mock
	private MonitorTreeQueryService monitorTreeQueryService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MonitorTreeController controller = new MonitorTreeController(monitorTreeQueryService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldReturnMonitorTree() throws Exception {
		when(monitorTreeQueryService.tree(eq(true), eq(200))).thenReturn(List.of(
			new MonitorAddressTreeView(
				1L,
				"0xabc",
				"wallet-1",
				true,
				List.of(
					new MonitorAddressTreeView.ScopeNode(
						11L,
						"ETH",
						"mainnet",
						true,
						List.of(
							new MonitorAddressTreeView.TokenNode(101L, "NATIVE", "ETH", 18, true)
						)
					)
				)
			)
		));

		mockMvc.perform(get("/api/monitor-tree"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].address", is("0xabc")))
			.andExpect(jsonPath("$[0].scopes[0].chain", is("ETH")))
			.andExpect(jsonPath("$[0].scopes[0].tokens[0].tokenContract", is("NATIVE")));

		verify(monitorTreeQueryService).tree(true, 200);
	}
}

