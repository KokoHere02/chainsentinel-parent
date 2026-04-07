package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.infra.service.PriceRuleEvaluatorService;
import com.chainsentinel.infra.service.RuleHitStatsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InternalRuleControllerTest {

  @Mock
  private PriceRuleEvaluatorService priceRuleEvaluatorService;

  @Mock
  private RuleHitStatsService ruleHitStatsService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    InternalRuleController controller = new InternalRuleController(priceRuleEvaluatorService, ruleHitStatsService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void shouldEvaluatePriceRules() throws Exception {
    when(priceRuleEvaluatorService.evaluateOnce()).thenReturn(2);

    mockMvc.perform(post("/api/internal/rules/price/evaluate"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.createdCount", is(2)))
      .andExpect(jsonPath("$.executedAt").exists());

    verify(priceRuleEvaluatorService).evaluateOnce();
  }

  @Test
  void shouldReturnRuleHitStats() throws Exception {
    when(ruleHitStatsService.list(true))
      .thenReturn(List.of(new RuleHitStatsService.RuleHitStatsView(
        7L,
        "btc-breakout",
        AlertRuleType.PRICE_THRESHOLD,
        true,
        12L,
        30L
      )));

    mockMvc.perform(get("/api/internal/rules/hit-stats"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].ruleId", is(7)))
      .andExpect(jsonPath("$[0].ruleName", is("btc-breakout")))
      .andExpect(jsonPath("$[0].type", is("PRICE_THRESHOLD")))
      .andExpect(jsonPath("$[0].hitCount24h", is(12)))
      .andExpect(jsonPath("$[0].hitCount7d", is(30)));

    verify(ruleHitStatsService).list(true);
  }
}
