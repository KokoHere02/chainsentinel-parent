package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.exception.RuleGovernanceException;
import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.rule.model.EventRuleCondition;
import com.chainsentinel.core.rule.model.EventRuleConditionItem;
import com.chainsentinel.core.rule.model.EventRuleField;
import com.chainsentinel.core.rule.model.EventRuleOperator;
import com.chainsentinel.core.rule.model.EventRuleSpec;
import com.chainsentinel.core.rule.model.PriceRuleCondition;
import com.chainsentinel.core.rule.model.PriceRuleOperator;
import com.chainsentinel.core.rule.model.PriceRuleSpec;
import com.chainsentinel.core.service.dto.AlertRuleCreateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
import com.chainsentinel.infra.rule.PriceRuleConditionParser;
import com.chainsentinel.infra.rule.RuleConditionJsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultAlertRuleServiceTest {

  @Mock
  private AlertRuleRepository alertRuleRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private DefaultAlertRuleService buildService() {
    RuleConditionJsonParser parser = new RuleConditionJsonParser(
      objectMapper,
      new EventRuleConditionParser(objectMapper),
      new PriceRuleConditionParser(objectMapper)
    );
    return new DefaultAlertRuleService(alertRuleRepository, parser);
  }

  @Test
  void shouldCreateAddressRuleAndSerializeConditionObject() throws Exception {
    DefaultAlertRuleService service = buildService();

    when(alertRuleRepository.save(any(AlertRuleEntity.class))).thenAnswer(invocation -> {
      AlertRuleEntity entity = invocation.getArgument(0);
      ReflectionTestUtils.setField(entity, "id", 7L);
      return entity;
    });

    EventRuleSpec spec = new EventRuleSpec(
      1,
      "EVENT",
      new EventRuleCondition(List.of(
        new EventRuleConditionItem(EventRuleField.CHAIN, EventRuleOperator.EQ, "ETH"),
        new EventRuleConditionItem(EventRuleField.NETWORK, EventRuleOperator.EQ, "sepolia"),
        new EventRuleConditionItem(EventRuleField.AMOUNT, EventRuleOperator.GTE, "100")
      ))
    );

    AlertRuleCreateCommand command = new AlertRuleCreateCommand(
      "address-watch",
      AlertRuleType.ADDRESS,
      objectMapper.valueToTree(spec),
      "HIGH",
      true
    );

    AlertRuleView view = service.create(command);

    assertEquals("address-watch", view.name());
    assertEquals(AlertRuleType.ADDRESS, view.type());
    assertEquals("HIGH", view.severity());
    assertTrue(view.enabled());
    assertEquals(1, objectMapper.readTree(view.conditionJson()).get("version").asInt());
  }

  @Test
  void shouldCreatePriceThresholdRule() throws Exception {
    DefaultAlertRuleService service = buildService();

    when(alertRuleRepository.save(any(AlertRuleEntity.class))).thenAnswer(invocation -> {
      AlertRuleEntity entity = invocation.getArgument(0);
      ReflectionTestUtils.setField(entity, "id", 13L);
      return entity;
    });

    PriceRuleSpec spec = new PriceRuleSpec();
    spec.setVersion(1);
    spec.setType("PRICE");
    PriceRuleCondition condition = new PriceRuleCondition();
    condition.setSymbol("BTC-USDT");
    condition.setOp(PriceRuleOperator.GTE);
    condition.setThreshold("100000");
    spec.setCondition(condition);

    AlertRuleView view = service.create(new AlertRuleCreateCommand(
      "btc-watch",
      AlertRuleType.PRICE_THRESHOLD,
      objectMapper.valueToTree(spec),
      "HIGH",
      true
    ));

    assertEquals(AlertRuleType.PRICE_THRESHOLD, view.type());
    assertEquals("btc-watch", view.name());
    assertEquals("BTC-USDT", objectMapper.readTree(view.conditionJson()).get("condition").get("symbol").asText());
  }

  @Test
  void shouldRejectFrequencyRuleByGovernance() {
    DefaultAlertRuleService service = buildService();

    EventRuleSpec spec = new EventRuleSpec(
      1,
      "EVENT",
      new EventRuleCondition(List.of(
        new EventRuleConditionItem(EventRuleField.CHAIN, EventRuleOperator.EQ, "ETH")
      ))
    );

    RuleGovernanceException ex = assertThrows(RuleGovernanceException.class, () -> service.create(new AlertRuleCreateCommand(
      "freq-rule",
      AlertRuleType.FREQUENCY,
      objectMapper.valueToTree(spec),
      "MEDIUM",
      true
    )));

    assertEquals("Rule type is disabled by governance: FREQUENCY", ex.getMessage());
    assertEquals("RULE_GOVERNANCE_REJECTED", ex.getCode());
    assertEquals(400, ex.getStatus());
  }

  @Test
  void shouldThrowIllegalArgumentWhenConditionIsInvalid() {
    DefaultAlertRuleService service = buildService();

    EventRuleSpec invalid = new EventRuleSpec();
    invalid.setVersion(1);
    invalid.setType("EVENT");

    AlertRuleCreateCommand command = new AlertRuleCreateCommand(
      "bad-rule",
      AlertRuleType.ADDRESS,
      objectMapper.valueToTree(invalid),
      "HIGH",
      true
    );

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(command));
    assertEquals("condition.all must be a non-empty array", ex.getMessage());
  }
}
