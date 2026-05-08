package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.exception.CoreErrorCode;
import com.chainsentinel.core.exception.TradeRiskException;
import com.chainsentinel.core.service.TradeOrderService;
import com.chainsentinel.core.service.dto.TradeFillView;
import com.chainsentinel.core.service.dto.TradeOrderCancelView;
import com.chainsentinel.core.service.dto.TradeOrderView;
import com.chainsentinel.web.api.support.GlobalExceptionHandler;
import com.chainsentinel.web.auth.audit.AuditEvent;
import com.chainsentinel.web.auth.audit.AuditEventPublisher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
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
class OrderControllerTest {

	@Mock
	private TradeOrderService tradeOrderService;

	@Mock
	private AuditEventPublisher auditEventPublisher;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(tradeOrderService, auditEventPublisher))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldCreateOrder() throws Exception {
		when(tradeOrderService.create(any(), any())).thenReturn(orderView());

		mockMvc.perform(post("/api/orders")
				.requestAttr("requestId", "rid-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountId": 1,
					  "symbol": "BTC-USDT",
					  "side": "BUY",
					  "orderType": "MARKET",
					  "quantity": 0.01
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("SUBMITTED")));

		ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
		verify(auditEventPublisher).publish(captor.capture());
		org.junit.jupiter.api.Assertions.assertEquals("ORDER_CREATE_SUCCESS", captor.getValue().action());
		org.junit.jupiter.api.Assertions.assertEquals("SUCCESS", captor.getValue().result());
		org.junit.jupiter.api.Assertions.assertEquals(
			"accountId=1,orderId=1,symbol=BTC-USDT,status=SUBMITTED",
			captor.getValue().reason()
		);
		org.junit.jupiter.api.Assertions.assertEquals("rid-1", captor.getValue().traceId());
	}

	@Test
	void shouldGetOrder() throws Exception {
		when(tradeOrderService.get(1L)).thenReturn(orderView());

		mockMvc.perform(get("/api/orders/1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.symbol", is("BTC-USDT")));
	}

	@Test
	void shouldListOrders() throws Exception {
		when(tradeOrderService.list(any())).thenReturn(List.of(orderView()));

		mockMvc.perform(get("/api/orders").param("accountId", "1").param("limit", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].clientOrderId", is("c1")));
	}

	@Test
	void shouldCancelOrder() throws Exception {
		when(tradeOrderService.cancel(anyLong(), any())).thenReturn(new TradeOrderCancelView(1L, "CANCELED", "order canceled", Instant.now()));

		mockMvc.perform(post("/api/orders/1/cancel"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("CANCELED")));
	}

	@Test
	void shouldRefreshOrder() throws Exception {
		when(tradeOrderService.refresh(anyLong(), any())).thenReturn(orderView());

		mockMvc.perform(post("/api/orders/1/refresh"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.providerOrderId", is("o1")));
	}

	@Test
	void shouldListFills() throws Exception {
		when(tradeOrderService.listFills(1L)).thenReturn(List.of(
			new TradeFillView(10L, 1L, "f1", "BTC-USDT", "BUY", new BigDecimal("100"), new BigDecimal("0.01"), BigDecimal.ZERO, "USDT", Instant.parse("2026-05-04T12:00:02Z"))
		));

		mockMvc.perform(get("/api/orders/1/fills"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].providerFillId", is("f1")));
	}

	@Test
	void shouldReturnNotFoundWhenMissing() throws Exception {
		when(tradeOrderService.get(99L)).thenThrow(new NoSuchElementException("trade order not found: 99"));

		mockMvc.perform(get("/api/orders/99"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldReturnTradeErrorCodeWhenCreateRejectedByRiskGate() throws Exception {
		when(tradeOrderService.create(any(), any()))
			.thenThrow(new TradeRiskException(CoreErrorCode.TRADE_DISABLED, "trade is disabled"));

		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountId": 1,
					  "symbol": "BTC-USDT",
					  "side": "BUY",
					  "orderType": "MARKET",
					  "quantity": 0.01
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("TRADE_DISABLED")));
	}

	@Test
	void shouldReturnTradeAccountInvalidCodeWhenCreateRejected() throws Exception {
		when(tradeOrderService.create(any(), any()))
			.thenThrow(new TradeRiskException(CoreErrorCode.TRADE_ACCOUNT_INVALID, "trade account apiKey is required: 1"));

		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountId": 1,
					  "symbol": "BTC-USDT",
					  "side": "BUY",
					  "orderType": "MARKET",
					  "quantity": 0.01
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("TRADE_ACCOUNT_INVALID")));
	}

	@Test
	void shouldReturnTradeRiskRejectedCodeWhenCreateRejected() throws Exception {
		when(tradeOrderService.create(any(), any()))
			.thenThrow(new TradeRiskException(CoreErrorCode.TRADE_RISK_REJECTED, "order quantity exceeds max limit"));

		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountId": 1,
					  "symbol": "BTC-USDT",
					  "side": "BUY",
					  "orderType": "MARKET",
					  "quantity": 0.01
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("TRADE_RISK_REJECTED")));
	}

	@Test
	void shouldAllowCancelEvenIfCreateWouldBeBlockedByTradeSwitch() throws Exception {
		when(tradeOrderService.cancel(anyLong(), any()))
			.thenReturn(new TradeOrderCancelView(1L, "CANCELED", "order canceled", Instant.now()));

		mockMvc.perform(post("/api/orders/1/cancel"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("CANCELED")));
	}

	private TradeOrderView orderView() {
		return new TradeOrderView(
			1L, 1L, "c1", "OKX", "SPOT", "BTC-USDT", "BUY", "MARKET",
			null, new BigDecimal("0.01"), null, "SUBMITTED", "o1", null, BigDecimal.ZERO, BigDecimal.ZERO,
			null, null, 1L, Instant.parse("2026-05-04T12:00:00Z"), Instant.parse("2026-05-04T12:00:01Z")
		);
	}
}
