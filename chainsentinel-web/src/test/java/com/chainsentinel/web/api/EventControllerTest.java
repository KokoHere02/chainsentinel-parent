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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventQueryService eventQueryService;

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

        org.junit.jupiter.api.Assertions.assertEquals("ETH", q.getValue().chain());
        org.junit.jupiter.api.Assertions.assertEquals("0xto", q.getValue().address());
        org.junit.jupiter.api.Assertions.assertEquals(EventStatus.CONFIRMED, q.getValue().status());
        org.junit.jupiter.api.Assertions.assertEquals(Instant.parse("2026-03-28T00:00:00Z"), q.getValue().startTime());
        org.junit.jupiter.api.Assertions.assertEquals(Instant.parse("2026-03-29T00:00:00Z"), q.getValue().endTime());
        org.junit.jupiter.api.Assertions.assertEquals(PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "blockNumber")), p.getValue());
    }
}



