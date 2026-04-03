package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.infra.service.PriceRuleEvaluatorService;
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

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    InternalRuleController controller = new InternalRuleController(priceRuleEvaluatorService);
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
}
