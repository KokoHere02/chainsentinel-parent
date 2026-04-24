package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.MonitorAddressScopeService;
import com.chainsentinel.core.service.dto.MonitorAddressScopeView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AddressScopeControllerTest {

	@Mock
	private MonitorAddressScopeService monitorAddressScopeService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		AddressScopeController controller = new AddressScopeController(monitorAddressScopeService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldListAddressScopes() throws Exception {
		when(monitorAddressScopeService.list(eq(1L), eq("ETH"), eq("mainnet"), eq(true), eq(20)))
			.thenReturn(List.of(
				new MonitorAddressScopeView(1L, 1L, "ETH", "mainnet", true),
				new MonitorAddressScopeView(2L, 1L, "ETH", "sepolia", true)
			));

		mockMvc.perform(get("/api/address-scopes")
				.param("monitorAddressId", "1")
				.param("chain", "ETH")
				.param("network", "mainnet")
				.param("enabled", "true")
				.param("limit", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].chain", is("ETH")));

		verify(monitorAddressScopeService).list(1L, "ETH", "mainnet", true, 20);
	}

	@Test
	void shouldReturnBadRequestWhenLimitInvalid() throws Exception {
		mockMvc.perform(get("/api/address-scopes").param("limit", "0"))
			.andExpect(status().isBadRequest());
	}
}

