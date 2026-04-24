package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.AddressHoldingQueryService;
import com.chainsentinel.core.service.dto.AddressTokenHoldingView;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class HoldingControllerTest {

	@Mock
	private AddressHoldingQueryService addressHoldingQueryService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		HoldingController controller = new HoldingController(addressHoldingQueryService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldListHoldings() throws Exception {
		when(addressHoldingQueryService.list(eq("ETH"), eq("mainnet"), eq("0xabc"), eq(30)))
			.thenReturn(List.of(
				new AddressTokenHoldingView(
					1L,
					1L,
					"ETH",
					"mainnet",
					"0xabc",
					"NATIVE",
					"ETH",
					18,
					"1000000000000000000",
					Instant.parse("2026-04-24T00:00:00Z")
				),
				new AddressTokenHoldingView(
					2L,
					2L,
					"ETH",
					"mainnet",
					"0xabc",
					"NATIVE",
					"ETH",
					18,
					"2000000000000000000",
					Instant.parse("2026-04-24T00:10:00Z")
				)
			));

		mockMvc.perform(get("/api/holdings")
				.param("chain", "ETH")
				.param("network", "mainnet")
				.param("address", "0xabc")
				.param("limit", "30"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].tokenContract", is("NATIVE")))
			.andExpect(jsonPath("$[0].balanceRaw", is("1000000000000000000")));

		verify(addressHoldingQueryService).list("ETH", "mainnet", "0xabc", 30);
	}

	@Test
	void shouldReturnBadRequestWhenLimitInvalid() throws Exception {
		mockMvc.perform(get("/api/holdings").param("limit", "0"))
			.andExpect(status().isBadRequest());
	}
}

