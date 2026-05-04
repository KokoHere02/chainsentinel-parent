package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.TradeAccountService;
import com.chainsentinel.core.service.TradeAccountAssetService;
import com.chainsentinel.core.service.dto.TradeAccountAssetSyncView;
import com.chainsentinel.core.service.dto.TradeAccountConnectivityTestView;
import com.chainsentinel.core.service.dto.TradeAccountBalanceSnapshotView;
import com.chainsentinel.core.service.dto.TradeAccountStreamStatusView;
import com.chainsentinel.core.service.dto.TradePositionSnapshotView;
import com.chainsentinel.core.service.dto.TradeAccountView;
import java.math.BigDecimal;
import java.time.Instant;
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
class TradeAccountControllerTest {

	@Mock
	private TradeAccountService tradeAccountService;

	@Mock
	private TradeAccountAssetService tradeAccountAssetService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		TradeAccountController controller = new TradeAccountController(tradeAccountService, tradeAccountAssetService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldCreate() throws Exception {
		when(tradeAccountService.create(any(), nullable(Long.class))).thenReturn(new TradeAccountView(
			1L, "okx-main", "OKX", "API_KEY", "SIMULATED", "api-****3456", true, true, true, "demo", 1L, 1L
		));

		mockMvc.perform(post("/api/trade/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  \"name\": \"okx-main\",
					  \"provider\": \"okx\",
					  \"accountType\": \"API_KEY\",
					  \"envType\": \"SIMULATED\",
					  \"apiKey\": \"api-key-123456\",
					  \"apiSecret\": \"secret-1\",
					  \"passphrase\": \"pass-1\",
					  \"enabled\": true,
					  \"remark\": \"demo\"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.provider", is("OKX")));
	}

	@Test
	void shouldList() throws Exception {
		when(tradeAccountService.list(true, "okx", "main", 20)).thenReturn(List.of(
			new TradeAccountView(1L, "okx-main", "OKX", "API_KEY", "SIMULATED", "api-****3456", true, true, true, "demo", 1L, 1L)
		));

		mockMvc.perform(get("/api/trade/accounts")
				.param("enabled", "true")
				.param("provider", "okx")
				.param("q", "main")
				.param("limit", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name", is("okx-main")));
	}

	@Test
	void shouldUpdate() throws Exception {
		when(tradeAccountService.update(any(), any(), nullable(Long.class))).thenReturn(new TradeAccountView(
			1L, "okx-live", "OKX", "API_KEY", "LIVE", "api-****3456", true, true, true, "live", 1L, 2L
		));

		mockMvc.perform(put("/api/trade/accounts/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  \"name\": \"okx-live\",
					  \"provider\": \"OKX\",
					  \"accountType\": \"API_KEY\",
					  \"envType\": \"LIVE\",
					  \"apiKey\": \"api-key-123456\",
					  \"enabled\": true,
					  \"remark\": \"live\"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.envType", is("LIVE")));
	}

	@Test
	void shouldEnable() throws Exception {
		when(tradeAccountService.setEnabled(1L, true, null)).thenReturn(new TradeAccountView(
			1L, "okx-main", "OKX", "API_KEY", "SIMULATED", "api-****3456", true, true, true, "demo", 1L, 1L
		));

		mockMvc.perform(patch("/api/trade/accounts/1/enable"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled", is(true)));
	}

	@Test
	void shouldTestConnectivity() throws Exception {
		when(tradeAccountService.testConnectivity(1L)).thenReturn(
			new TradeAccountConnectivityTestView(1L, "OKX", true, "OKX connectivity check passed", Instant.parse("2026-05-04T12:00:00Z"))
		);

		mockMvc.perform(post("/api/trade/accounts/1/test-connectivity"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success", is(true)));
	}

	@Test
	void shouldReturnNotFoundWhenGetMissing() throws Exception {
		when(tradeAccountService.get(999L)).thenThrow(new NoSuchElementException("trade account not found: 999"));

		mockMvc.perform(get("/api/trade/accounts/999"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldDelete() throws Exception {
		mockMvc.perform(delete("/api/trade/accounts/1"))
			.andExpect(status().isOk());
		verify(tradeAccountService).delete(1L);
	}

	@Test
	void shouldGetWsStatuses() throws Exception {
		when(tradeAccountService.streamStatuses()).thenReturn(List.of(
			new TradeAccountStreamStatusView(
				1L, "OKX", true, true, true, true, true,
				Instant.parse("2026-05-04T12:00:00Z"),
				Instant.parse("2026-05-04T12:00:00Z"),
				Instant.parse("2026-05-04T12:00:00Z"),
				null, null
			)
		));

		mockMvc.perform(get("/api/trade/accounts/ws-status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].connected", is(true)));
	}

	@Test
	void shouldSyncAssets() throws Exception {
		when(tradeAccountAssetService.sync(anyLong(), nullable(Long.class))).thenReturn(
			new TradeAccountAssetSyncView(1L, 2, 1, Instant.parse("2026-05-04T12:00:00Z"))
		);

		mockMvc.perform(post("/api/trade/accounts/1/sync-assets"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.balanceCount", is(2)));
	}

	@Test
	void shouldListBalances() throws Exception {
		when(tradeAccountAssetService.listLatestBalances(1L)).thenReturn(List.of(
			new TradeAccountBalanceSnapshotView(1L, 1L, "USDT", new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"), "WS", Instant.parse("2026-05-04T12:00:00Z"))
		));

		mockMvc.perform(get("/api/trade/accounts/1/balances"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].asset", is("USDT")));
	}

	@Test
	void shouldListPositions() throws Exception {
		when(tradeAccountAssetService.listLatestPositions(1L)).thenReturn(List.of(
			new TradePositionSnapshotView(1L, 1L, "BTC-USDT", "BTC", "USDT", new BigDecimal("0.01"), null, new BigDecimal("100000"), new BigDecimal("1000"), null, null, "WS", Instant.parse("2026-05-04T12:00:00Z"))
		));

		mockMvc.perform(get("/api/trade/accounts/1/positions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].symbol", is("BTC-USDT")));
	}
}
