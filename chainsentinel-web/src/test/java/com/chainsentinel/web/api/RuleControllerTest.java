package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.service.AlertRuleService;
import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleQueryCommand;
import com.chainsentinel.core.service.dto.AlertRuleUpdateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.chainsentinel.core.exception.NotFoundException;
import com.chainsentinel.web.api.support.GlobalExceptionHandler;
import java.util.List;
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
    mockMvc = MockMvcBuilders
      .standaloneSetup(controller)
      .setControllerAdvice(new GlobalExceptionHandler())
      .build();
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

  @Test
  void shouldUpdatePriceRule() throws Exception {
    when(alertRuleService.update(any(AlertRuleUpdateCommand.class)))
      .thenReturn(new AlertRuleView(9L, "btc-update", AlertRuleType.PRICE_THRESHOLD, "{}", "HIGH", false));

    mockMvc.perform(put("/api/rules/9")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "name": "btc-update",
            "condition": {
              "version": 1,
              "type": "PRICE",
              "condition": {
                "symbol": "BTC-USDT",
                "op": "gte",
                "threshold": "100000",
                "cooldownSec": 60
              }
            },
            "severity": "HIGH",
            "enabled": false
          }
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(9)))
      .andExpect(jsonPath("$.type", is("PRICE_THRESHOLD")))
      .andExpect(jsonPath("$.enabled", is(false)));

    ArgumentCaptor<AlertRuleUpdateCommand> captor = ArgumentCaptor.forClass(AlertRuleUpdateCommand.class);
    verify(alertRuleService).update(captor.capture());
    AlertRuleUpdateCommand cmd = captor.getValue();
    Assertions.assertEquals(9L, cmd.id());
    Assertions.assertEquals("btc-update", cmd.name());
    Assertions.assertEquals("HIGH", cmd.severity());
    Assertions.assertEquals(false, cmd.enabled());
    Assertions.assertEquals(1, cmd.condition().get("version").asInt());
  }

  @Test
  void shouldListRulesWithTypeAndEnabledFilters() throws Exception {
    when(alertRuleService.list(any(AlertRuleQueryCommand.class)))
      .thenReturn(List.of(new AlertRuleView(5L, "price-1", AlertRuleType.PRICE_THRESHOLD, "{}", "HIGH", true)));

    mockMvc.perform(get("/api/rules")
        .param("type", "PRICE_THRESHOLD")
        .param("enabled", "true"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id", is(5)))
      .andExpect(jsonPath("$[0].type", is("PRICE_THRESHOLD")))
      .andExpect(jsonPath("$[0].enabled", is(true)));

    ArgumentCaptor<AlertRuleQueryCommand> captor = ArgumentCaptor.forClass(AlertRuleQueryCommand.class);
    verify(alertRuleService).list(captor.capture());
    AlertRuleQueryCommand cmd = captor.getValue();
    Assertions.assertEquals(AlertRuleType.PRICE_THRESHOLD, cmd.type());
    Assertions.assertEquals(true, cmd.enabled());
  }

  @Test
  void shouldDeleteRule() throws Exception {
    when(alertRuleService.delete(12L))
      .thenReturn(new AlertRuleView(12L, "r-del", AlertRuleType.ADDRESS, "{}", "HIGH", false));

    mockMvc.perform(delete("/api/rules/12"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(12)))
      .andExpect(jsonPath("$.enabled", is(false)));

    verify(alertRuleService).delete(12L);
  }

  @Test
  void shouldGetRuleById() throws Exception {
    when(alertRuleService.getById(15L))
      .thenReturn(new AlertRuleView(15L, "r-detail", AlertRuleType.AMOUNT, "{}", "HIGH", true));

    mockMvc.perform(get("/api/rules/15"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(15)))
      .andExpect(jsonPath("$.name", is("r-detail")))
      .andExpect(jsonPath("$.type", is("AMOUNT")));

    verify(alertRuleService).getById(15L);
  }

  @Test
  void shouldReturn404WhenGetRuleByIdNotFound() throws Exception {
    when(alertRuleService.getById(404L)).thenThrow(new NotFoundException("Rule not found: 404"));

    mockMvc.perform(get("/api/rules/404"))
      .andExpect(status().isNotFound());
  }
}
