package com.chainsentinel.infra.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.model.TokenType;
import com.chainsentinel.core.rule.model.EventRuleCondition;
import com.chainsentinel.core.rule.model.EventRuleConditionItem;
import com.chainsentinel.core.rule.model.EventRuleField;
import com.chainsentinel.core.rule.model.EventRuleOperator;
import com.chainsentinel.core.rule.model.EventRuleSpec;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventRuleConditionParserTest {

    private final EventRuleConditionParser parser = new EventRuleConditionParser(new ObjectMapper());

    @Test
    void shouldSerializeAndParseRuleObject() {
        EventRuleSpec spec = new EventRuleSpec(
                1,
                "EVENT",
                new EventRuleCondition(List.of(
                        new EventRuleConditionItem(EventRuleField.CHAIN, EventRuleOperator.EQ, "ETH"),
                        new EventRuleConditionItem(EventRuleField.AMOUNT, EventRuleOperator.GTE, "10")
                ))
        );

        String json = parser.serialize(spec);
        EventRuleSpec parsed = parser.parse(json);

        assertEquals(1, parsed.getVersion());
        assertEquals("EVENT", parsed.getType());
        assertEquals(2, parsed.getCondition().getAll().size());
    }

    @Test
    void shouldMatchComposedRule() {
        EventRuleSpec spec = new EventRuleSpec(
                1,
                "EVENT",
                new EventRuleCondition(List.of(
                        new EventRuleConditionItem(EventRuleField.CHAIN, EventRuleOperator.EQ, "ETH"),
                        new EventRuleConditionItem(EventRuleField.NETWORK, EventRuleOperator.EQ, "sepolia"),
                        new EventRuleConditionItem(EventRuleField.FROM_ADDRESS, EventRuleOperator.EQ, "0xabc"),
                        new EventRuleConditionItem(EventRuleField.AMOUNT, EventRuleOperator.LTE, "100")
                ))
        );

        AssetEventEntity event = new AssetEventEntity();
        event.setChain("ETH");
        event.setNetwork("sepolia");
        event.setFromAddress("0xAbC");
        event.setAmount("99");

        assertTrue(parser.matches(spec, event));
    }

    @Test
    void shouldCompareAmountByMinimalUnitValue() {
        EventRuleSpec spec = new EventRuleSpec(
                1,
                "EVENT",
                new EventRuleCondition(List.of(
                        new EventRuleConditionItem(EventRuleField.AMOUNT, EventRuleOperator.EQ, "100")
                ))
        );

        AssetEventEntity event = new AssetEventEntity();
        event.setAmount("000100");

        assertTrue(parser.matches(spec, event));
    }

    @Test
    void shouldRejectDecimalAmountValue() {
        EventRuleSpec spec = new EventRuleSpec(
                1,
                "EVENT",
                new EventRuleCondition(List.of(
                        new EventRuleConditionItem(EventRuleField.AMOUNT, EventRuleOperator.GTE, "1.5")
                ))
        );

        AssetEventEntity event = new AssetEventEntity();
        event.setAmount("2");

        assertThrows(IllegalArgumentException.class, () -> parser.matches(spec, event));
    }

    @Test
    void shouldSupportInOperator() {
        EventRuleSpec spec = new EventRuleSpec(
                1,
                "EVENT",
                new EventRuleCondition(List.of(
                        new EventRuleConditionItem(EventRuleField.TO_ADDRESS, EventRuleOperator.IN, List.of("0x111", "0x222"))
                ))
        );

        AssetEventEntity event = new AssetEventEntity();
        event.setToAddress("0x222");

        assertTrue(parser.matches(spec, event));
        event.setToAddress("0x333");
        assertFalse(parser.matches(spec, event));
    }

    @Test
    void shouldSupportEnumFields() {
        EventRuleSpec spec = new EventRuleSpec(
                1,
                "EVENT",
                new EventRuleCondition(List.of(
                        new EventRuleConditionItem(EventRuleField.TOKEN_TYPE, EventRuleOperator.EQ, "ERC20"),
                        new EventRuleConditionItem(EventRuleField.STATUS, EventRuleOperator.EQ, "CONFIRMED")
                ))
        );

        AssetEventEntity event = new AssetEventEntity();
        event.setTokenType(TokenType.ERC20);
        event.setStatus(EventStatus.CONFIRMED);

        assertTrue(parser.matches(spec, event));
    }

    @Test
    void shouldRejectInvalidOpFromJson() {
        String json = """
                {
                  \"version\": 1,
                  \"type\": \"EVENT\",
                  \"condition\": {
                    \"all\": [
                      {\"field\":\"chain\",\"op\":\"contains\",\"value\":\"ETH\"}
                    ]
                  }
                }
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(json));
    }

    @Test
    void shouldRejectUnsupportedVersion() {
        EventRuleSpec spec = new EventRuleSpec(
                2,
                "EVENT",
                new EventRuleCondition(List.of(
                        new EventRuleConditionItem(EventRuleField.CHAIN, EventRuleOperator.EQ, "ETH")
                ))
        );

        assertThrows(IllegalArgumentException.class, () -> parser.serialize(spec));
    }
}
