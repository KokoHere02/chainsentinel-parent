package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.MonitorAddressService;
import com.chainsentinel.core.service.dto.MonitorAddressView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AddressControllerTest {

	@Mock
	private MonitorAddressService monitorAddressService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		AddressController controller = new AddressController(monitorAddressService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldListAddresses() throws Exception {
		when(monitorAddressService.list(eq("ETH"), eq("0xabc"), eq(true), eq(30)))
			.thenReturn(List.of(
				new MonitorAddressView(1L, "ETH", "0xabc1", "wallet-1", true),
				new MonitorAddressView(2L, "ETH", "0xabc2", "wallet-2", true)
			));

		mockMvc.perform(get("/api/addresses")
				.param("q", "0xabc")
				.param("chain", "ETH")
				.param("enabled", "true")
				.param("limit", "30"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].address", is("0xabc1")));

		verify(monitorAddressService).list("ETH", "0xabc", true, 30);
	}

	@Test
	void shouldSearchAddresses() throws Exception {
		when(monitorAddressService.search(eq("ETH"), eq("0xabc"), eq(10), eq(true)))
			.thenReturn(List.of(
				new MonitorAddressView(1L, "ETH", "0xabc1", "wallet-1", true),
				new MonitorAddressView(2L, "ETH", "0xabc2", "wallet-2", true)
			));

		mockMvc.perform(get("/api/addresses/search")
				.param("q", "0xabc")
				.param("chain", "ETH")
				.param("limit", "10")
				.param("enabledOnly", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].address", is("0xabc1")))
			.andExpect(jsonPath("$[1].address", is("0xabc2")));

		verify(monitorAddressService).search("ETH", "0xabc", 10, true);
	}

	@Test
	void shouldReturnBadRequestWhenLimitInvalid() throws Exception {
		mockMvc.perform(get("/api/addresses/search").param("limit", "0"))
			.andExpect(status().isBadRequest());
	}
}