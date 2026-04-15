package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.model.TokenType;
import com.chainsentinel.core.service.EventQueryService;
import com.chainsentinel.core.service.dto.EventQuery;
import com.chainsentinel.core.service.dto.EventView;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

	@Mock
	private EventQueryService eventQueryService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		EventController controller = new EventController(eventQueryService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldQueryEventsWithFiltersAndPaging() throws Exception {
		EventView view = new EventView(
			1L,
			"ETH",
			"mainnet",
			10L,
			"0xtx",
			0,
			"0xfrom",
			"0xto",
			TokenType.ETH,
			"ETH",
			"1000000000000000000",
			EventStatus.CONFIRMED,
			12,
			Instant.parse("2026-03-28T12:00:00Z")
		);
		Page<EventView> page = new PageImpl<>(List.of(view), PageRequest.of(1, 5), 1);
		when(eventQueryService.query(any(EventQuery.class), any(PageRequest.class))).thenReturn(page);

		mockMvc.perform(get("/api/events")
				.param("chain", "ETH")
				.param("address", "0xto")
				.param("status", "CONFIRMED")
				.param("startTime", "2026-03-28T00:00:00Z")
				.param("endTime", "2026-03-29T00:00:00Z")
				.param("page", "1")
				.param("size", "5"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id", is(1)))
			.andExpect(jsonPath("$.content[0].chain", is("ETH")))
			.andExpect(jsonPath("$.content[0].status", is("CONFIRMED")));

		ArgumentCaptor<EventQuery> q = ArgumentCaptor.forClass(EventQuery.class);
		ArgumentCaptor<org.springframework.data.domain.Pageable> p = ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
		verify(eventQueryService).query(q.capture(), p.capture());

		Assertions.assertEquals("ETH", q.getValue().chain());
		Assertions.assertEquals("0xto", q.getValue().address());
		Assertions.assertEquals(EventStatus.CONFIRMED, q.getValue().status());
		Assertions.assertEquals(Instant.parse("2026-03-28T00:00:00Z"), q.getValue().startTime());
		Assertions.assertEquals(Instant.parse("2026-03-29T00:00:00Z"), q.getValue().endTime());
		Assertions.assertEquals(PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "blockNumber")), p.getValue());
	}

	@Test
	void shouldQueryTransferEventsWithExtendedFilters() throws Exception {
		EventView view = new EventView(
			2L,
			"ETH",
			"sepolia",
			11L,
			"0xtx2",
			1,
			"0xfrom2",
			"0xto2",
			TokenType.ERC20,
			"USDT",
			"1000000",
			EventStatus.PENDING,
			0,
			Instant.parse("2026-03-28T13:00:00Z")
		);
		Page<EventView> page = new PageImpl<>(List.of(view), PageRequest.of(0, 20), 1);
		when(eventQueryService.query(any(EventQuery.class), any(PageRequest.class))).thenReturn(page);

		mockMvc.perform(get("/api/events/transfers")
				.param("chain", "ETH")
				.param("network", "sepolia")
				.param("address", "0xaddr")
				.param("fromAddress", "0xfrom2")
				.param("toAddress", "0xto2")
				.param("symbol", "USDT")
				.param("txHash", "0xtx2")
				.param("status", "PENDING")
				.param("startTime", "2026-03-28T00:00:00Z")
				.param("endTime", "2026-03-29T00:00:00Z"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id", is(2)))
			.andExpect(jsonPath("$.content[0].symbol", is("USDT")));

		ArgumentCaptor<EventQuery> q = ArgumentCaptor.forClass(EventQuery.class);
		verify(eventQueryService).query(q.capture(), any(PageRequest.class));

		Assertions.assertEquals("ETH", q.getValue().chain());
		Assertions.assertEquals("sepolia", q.getValue().network());
		Assertions.assertEquals("0xaddr", q.getValue().address());
		Assertions.assertEquals("0xfrom2", q.getValue().fromAddress());
		Assertions.assertEquals("0xto2", q.getValue().toAddress());
		Assertions.assertEquals("USDT", q.getValue().symbol());
		Assertions.assertEquals("0xtx2", q.getValue().txHash());
		Assertions.assertEquals(EventStatus.PENDING, q.getValue().status());
	}
}