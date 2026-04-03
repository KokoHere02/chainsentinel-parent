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
import org.junit.jupiter.api.Assertions;
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
class RuleControllerTest {

  @Mock
  private AlertRuleService alertRuleService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    RuleController controller = new RuleController(alertRuleService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void shouldCreateAddressRuleWithDefaultEnabled() throws Exception {
    when(alertRuleService.create(any(AlertRuleCreateCommand.class)))
      .thenReturn(new AlertRuleView(2L, "r1", AlertRuleType.ADDRESS, "{}", "HIGH", true));

    mockMvc.perform(post("/api/rules")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "name": "r1",
            "type": "ADDRESS",
            "condition": {
              "version": 1,
              "type": "EVENT",
              "condition": {
                "all": [
                  {"field": "chain", "op": "eq", "value": "ETH"}
                ]
              }
            },
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
    Assertions.assertEquals("r1", cmd.name());
    Assertions.assertEquals(AlertRuleType.ADDRESS, cmd.type());
    Assertions.assertEquals("HIGH", cmd.severity());
    Assertions.assertEquals(true, cmd.enabled());
    Assertions.assertEquals(1, cmd.condition().get("version").asInt());
  }

  @Test
  void shouldCreateAmountRule() throws Exception {
    when(alertRuleService.create(any(AlertRuleCreateCommand.class)))
      .thenReturn(new AlertRuleView(3L, "amount-rule", AlertRuleType.AMOUNT, "{}", "CRITICAL", true));

    mockMvc.perform(post("/api/rules")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "name": "amount-rule",
            "type": "AMOUNT",
            "condition": {
              "version": 1,
              "type": "EVENT",
              "condition": {
                "all": [
                  {"field": "amount", "op": "gte", "value": "1000000000000000000"}
                ]
              }
            },
            "severity": "CRITICAL",
            "enabled": true
          }
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.type", is("AMOUNT")));

    ArgumentCaptor<AlertRuleCreateCommand> captor = ArgumentCaptor.forClass(AlertRuleCreateCommand.class);
    verify(alertRuleService).create(captor.capture());
    AlertRuleCreateCommand cmd = captor.getValue();
    Assertions.assertEquals("amount-rule", cmd.name());
    Assertions.assertEquals(AlertRuleType.AMOUNT, cmd.type());
  }

  @Test
  void shouldReturn400WhenConditionMissing() throws Exception {
    mockMvc.perform(post("/api/rules")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "name": "r1",
            "type": "ADDRESS",
            "severity": "HIGH"
          }
          """))
      .andExpect(status().isBadRequest());
  }
}
