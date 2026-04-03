package com.chainsentinel.infra.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chainsentinel.core.rule.model.PriceRuleCondition;
import com.chainsentinel.core.rule.model.PriceRuleOperator;
import com.chainsentinel.core.rule.model.PriceRuleSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PriceRuleConditionParserTest {

  private final PriceRuleConditionParser parser = new PriceRuleConditionParser(new ObjectMapper());

  @Test
  void shouldSerializeAndParseRuleObject() {
    PriceRuleSpec spec = buildSpec("BTC-USDT", PriceRuleOperator.GTE, "100");

    String json = parser.serialize(spec);
    PriceRuleSpec parsed = parser.parse(json);

    assertEquals(1, parsed.getVersion());
    assertEquals("PRICE", parsed.getType());
    assertEquals("BTC-USDT", parsed.getCondition().getSymbol());
  }

  @Test
  void shouldMatchByThreshold() {
    PriceRuleSpec spec = buildSpec("BTC-USDT", PriceRuleOperator.GT, "100");

    assertTrue(parser.matches(spec, new BigDecimal("100.01")));
    assertFalse(parser.matches(spec, new BigDecimal("100.00")));
  }

  @Test
  void shouldRejectInvalidThreshold() {
    PriceRuleSpec spec = buildSpec("BTC-USDT", PriceRuleOperator.GTE, "abc");

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.serialize(spec));
    assertTrue(ex.getMessage().contains("condition.threshold is invalid decimal"));
  }

  private PriceRuleSpec buildSpec(String symbol, PriceRuleOperator op, String threshold) {
    PriceRuleCondition condition = new PriceRuleCondition();
    condition.setSymbol(symbol);
    condition.setOp(op);
    condition.setThreshold(threshold);

    PriceRuleSpec spec = new PriceRuleSpec();
    spec.setVersion(1);
    spec.setType("PRICE");
    spec.setCondition(condition);
    return spec;
  }
}
