package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.web.api.support.GlobalExceptionHandler;
import com.chainsentinel.web.auth.AdminTradeAuditService;
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
class AdminTradeAuditControllerTest {

	@Mock
	private AdminTradeAuditService adminTradeAuditService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new AdminTradeAuditController(adminTradeAuditService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldReturnOrderCreateAuditRows() throws Exception {
		when(adminTradeAuditService.listOrderCreateAudits("FAIL", "admin", "TRADE_DISABLED",
			Instant.parse("2026-05-08T00:00:00Z"), Instant.parse("2026-05-09T00:00:00Z"), 0, 100))
			.thenReturn(List.of(
				new AdminTradeAuditService.OrderCreateAuditView(
					11L,
					"ORDER_CREATE_FAIL",
					"FAIL",
					1L,
					"admin",
					"TRADE_DISABLED",
					null,
					null,
					null,
					null,
					"code=TRADE_DISABLED,message=trade is disabled",
					"t1",
					"127.0.0.1",
					Instant.parse("2026-05-08T03:00:00Z")
				)
			));

		mockMvc.perform(get("/api/admin/trade-audit/order-create")
				.param("result", "FAIL")
				.param("username", "admin")
				.param("rejectCode", "TRADE_DISABLED")
				.param("from", "2026-05-08T00:00:00Z")
				.param("to", "2026-05-09T00:00:00Z"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].action", is("ORDER_CREATE_FAIL")))
			.andExpect(jsonPath("$[0].rejectCode", is("TRADE_DISABLED")));
	}

	@Test
	void shouldPassRawPagingArgumentsToService() throws Exception {
		when(adminTradeAuditService.listOrderCreateAudits(null, null, null, null, null, -1, 999))
			.thenReturn(List.of());

		mockMvc.perform(get("/api/admin/trade-audit/order-create")
				.param("page", "-1")
				.param("size", "999"))
			.andExpect(status().isOk());

		verify(adminTradeAuditService).listOrderCreateAudits(null, null, null, null, null, -1, 999);
	}

	@Test
	void shouldReturnOrderCreateAuditSummary() throws Exception {
		when(adminTradeAuditService.summarizeOrderCreateAudits(
			"admin",
			null,
			Instant.parse("2026-05-08T00:00:00Z"),
			Instant.parse("2026-05-09T00:00:00Z"),
			10
		)).thenReturn(new AdminTradeAuditService.OrderCreateAuditSummaryView(
			10L,
			8L,
			2L,
			new java.math.BigDecimal("0.200000"),
			List.of(new AdminTradeAuditService.RejectCodeCountView("TRADE_DISABLED", 2L))
		));

		mockMvc.perform(get("/api/admin/trade-audit/order-create/summary")
				.param("username", "admin")
				.param("from", "2026-05-08T00:00:00Z")
				.param("to", "2026-05-09T00:00:00Z")
				.param("top", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount", is(10)))
			.andExpect(jsonPath("$.successCount", is(8)))
			.andExpect(jsonPath("$.rejectCount", is(2)))
			.andExpect(jsonPath("$.rejectRate", is(0.2)))
			.andExpect(jsonPath("$.topRejectCodes", hasSize(1)))
			.andExpect(jsonPath("$.topRejectCodes[0].rejectCode", is("TRADE_DISABLED")));
	}

	@Test
	void shouldReturnOrderCreateAuditTrend() throws Exception {
		when(adminTradeAuditService.trendOrderCreateAudits(
			"admin",
			Instant.parse("2026-05-08T00:00:00Z"),
			Instant.parse("2026-05-08T03:00:00Z"),
			3600L
		)).thenReturn(List.of(
			new AdminTradeAuditService.OrderCreateTrendPointView(
				1746662400000L,
				3L,
				2L,
				1L,
				new java.math.BigDecimal("0.333333")
			)
		));

		mockMvc.perform(get("/api/admin/trade-audit/order-create/trend")
				.param("username", "admin")
				.param("from", "2026-05-08T00:00:00Z")
				.param("to", "2026-05-08T03:00:00Z")
				.param("bucketSec", "3600"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].bucketStartTs", is(1746662400000L)))
			.andExpect(jsonPath("$[0].totalCount", is(3)))
			.andExpect(jsonPath("$[0].successCount", is(2)))
			.andExpect(jsonPath("$[0].rejectCount", is(1)))
			.andExpect(jsonPath("$[0].rejectRate", is(0.333333)));
	}
}
