package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.AlertDispatchService;
import com.chainsentinel.core.service.AlertQueryService;
import com.chainsentinel.core.service.dto.AlertQuery;
import com.chainsentinel.core.service.dto.AlertView;
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

@WebMvcTest(AlertController.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertQueryService alertQueryService;

    @MockBean
    private AlertDispatchService alertDispatchService;

    @Test
    void shouldListAlertsAndPassQuery() throws Exception {
        AlertView view = new AlertView(1L, 2L, 3L, "HIGH", "PENDING", 0, null, Instant.parse("2026-03-28T12:00:00Z"));
        Page<AlertView> page = new PageImpl<>(List.of(view), PageRequest.of(0, 10), 1);
        when(alertQueryService.query(any(AlertQuery.class), any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/alerts")
                        .param("sendStatus", "PENDING")
                        .param("severity", "HIGH")
                        .param("ruleId", "2")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(1)))
                .andExpect(jsonPath("$.content[0].sendStatus", is("PENDING")));

        ArgumentCaptor<AlertQuery> q = ArgumentCaptor.forClass(AlertQuery.class);
        ArgumentCaptor<org.springframework.data.domain.Pageable> p = ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(alertQueryService).query(q.capture(), p.capture());

        org.junit.jupiter.api.Assertions.assertEquals("PENDING", q.getValue().sendStatus());
        org.junit.jupiter.api.Assertions.assertEquals("HIGH", q.getValue().severity());
        org.junit.jupiter.api.Assertions.assertEquals(2L, q.getValue().ruleId());
        org.junit.jupiter.api.Assertions.assertEquals(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")), p.getValue());
    }

    @Test
    void shouldRetryAlert() throws Exception {
        when(alertDispatchService.retryOne(9L)).thenReturn(true);

        mockMvc.perform(post("/api/alerts/retry/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        verify(alertDispatchService).retryOne(9L);
    }
}
