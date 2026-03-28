package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.service.AlertRuleService;
import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RuleController.class)
class RuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertRuleService alertRuleService;

    @Test
    void shouldCreateRuleWithDefaultEnabled() throws Exception {
        when(alertRuleService.create(any(AlertRuleCreateCommand.class)))
                .thenReturn(new AlertRuleView(2L, "r1", AlertRuleType.ADDRESS, "{}", "HIGH", true));

        mockMvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "r1",
                                  "type": "ADDRESS",
                                  "condition": {"k":"v"},
                                  "severity": "HIGH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(2)))
                .andExpect(jsonPath("$.type", is("ADDRESS")))
                .andExpect(jsonPath("$.enabled", is(true)));

        ArgumentCaptor<AlertRuleCreateCommand> captor = ArgumentCaptor.forClass(AlertRuleCreateCommand.class);
        verify(alertRuleService).create(captor.capture());
        AlertRuleCreateCommand cmd = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("r1", cmd.name());
        org.junit.jupiter.api.Assertions.assertEquals(AlertRuleType.ADDRESS, cmd.type());
        org.junit.jupiter.api.Assertions.assertEquals("HIGH", cmd.severity());
        org.junit.jupiter.api.Assertions.assertEquals(true, cmd.enabled());
    }

    @Test
    void shouldReturn400WhenTypeMissing() throws Exception {
        mockMvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "r1",
                                  "severity": "HIGH"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
