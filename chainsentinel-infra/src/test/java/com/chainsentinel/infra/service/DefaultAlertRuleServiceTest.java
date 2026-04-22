package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.exception.NotFoundException;
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
import com.chainsentinel.core.service.dto.AlertRulePatchConditionCommand;
import com.chainsentinel.core.service.dto.AlertRuleQueryCommand;
import com.chainsentinel.core.service.dto.AlertRuleUpdateCommand;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
import com.chainsentinel.infra.rule.PriceRuleConditionParser;
import com.chainsentinel.infra.rule.RuleConditionJsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
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
      AlertRuleType.EVENT,
      objectMapper.valueToTree(spec),
      "HIGH",
      true
    );

    AlertRuleView view = service.create(command);

    assertEquals("address-watch", view.name());
    assertEquals(AlertRuleType.EVENT, view.type());
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
    assertEquals(0, objectMapper.readTree(view.conditionJson()).get("condition").get("cooldownSec").asInt());
  }

  @Test
  void shouldRejectPriceThresholdRuleWhenCooldownSecExceedsLimit() {
    DefaultAlertRuleService service = buildService();

    PriceRuleSpec spec = new PriceRuleSpec();
    spec.setVersion(1);
    spec.setType("PRICE");
    PriceRuleCondition condition = new PriceRuleCondition();
    condition.setSymbol("BTC-USDT");
    condition.setOp(PriceRuleOperator.GTE);
    condition.setThreshold("100000");
    condition.setCooldownSec(86401);
    spec.setCondition(condition);

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(new AlertRuleCreateCommand(
      "btc-watch-too-long-cooldown",
      AlertRuleType.PRICE_THRESHOLD,
      objectMapper.valueToTree(spec),
      "HIGH",
      true
    )));
    assertEquals("condition.cooldownSec must be <= 86400", ex.getMessage());
  }

  @Test
  void shouldThrowIllegalArgumentWhenConditionIsInvalid() {
    DefaultAlertRuleService service = buildService();

    EventRuleSpec invalid = new EventRuleSpec();
    invalid.setVersion(1);
    invalid.setType("EVENT");

    AlertRuleCreateCommand command = new AlertRuleCreateCommand(
      "bad-rule",
      AlertRuleType.EVENT,
      objectMapper.valueToTree(invalid),
      "HIGH",
      true
    );

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(command));
    assertEquals("condition.all must be a non-empty array", ex.getMessage());
  }

  @Test
  void shouldRejectCreateWhenEventRuleContainsAddressField() {
    DefaultAlertRuleService service = buildService();

    EventRuleSpec spec = new EventRuleSpec(
      1,
      "EVENT",
      new EventRuleCondition(List.of(
        new EventRuleConditionItem(EventRuleField.CHAIN, EventRuleOperator.EQ, "ETH"),
        new EventRuleConditionItem(EventRuleField.TO_ADDRESS, EventRuleOperator.EQ, "0xabc")
      ))
    );

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(new AlertRuleCreateCommand(
      "bad-event-rule",
      AlertRuleType.EVENT,
      objectMapper.valueToTree(spec),
      "HIGH",
      true
    )));
    assertEquals("Event rule must not contain address field: to_address", ex.getMessage());
  }

  @Test
  void shouldUpdatePriceRuleSuccessfully() throws Exception {
    DefaultAlertRuleService service = buildService();

    AlertRuleEntity existing = new AlertRuleEntity();
    ReflectionTestUtils.setField(existing, "id", 21L);
    existing.setName("old-name");
    existing.setType(AlertRuleType.PRICE_THRESHOLD);
    existing.setSeverity("MEDIUM");
    existing.setEnabled(true);
    existing.setConditionJson("{\"version\":1,\"type\":\"PRICE\",\"condition\":{\"symbol\":\"BTC-USDT\",\"op\":\"gte\",\"threshold\":\"100000\",\"cooldownSec\":0}}");

    when(alertRuleRepository.findById(21L)).thenReturn(Optional.of(existing));
    when(alertRuleRepository.save(any(AlertRuleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    PriceRuleSpec spec = new PriceRuleSpec();
    spec.setVersion(1);
    spec.setType("PRICE");
    PriceRuleCondition condition = new PriceRuleCondition();
    condition.setSymbol("ETH-USDT");
    condition.setOp(PriceRuleOperator.GTE);
    condition.setThreshold("3000");
    condition.setCooldownSec(120);
    spec.setCondition(condition);

    AlertRuleView view = service.update(new AlertRuleUpdateCommand(
      21L,
      "new-name",
      objectMapper.valueToTree(spec),
      "HIGH",
      false
    ));

    assertEquals(21L, view.id());
    assertEquals("new-name", view.name());
    assertEquals(AlertRuleType.PRICE_THRESHOLD, view.type());
    assertEquals("HIGH", view.severity());
    assertEquals(false, view.enabled());
    assertEquals("ETH-USDT", objectMapper.readTree(view.conditionJson()).get("condition").get("symbol").asText());
    assertEquals(120, objectMapper.readTree(view.conditionJson()).get("condition").get("cooldownSec").asInt());
  }

  @Test
  void shouldRejectUpdateWhenCooldownSecExceedsLimit() {
    DefaultAlertRuleService service = buildService();

    AlertRuleEntity existing = new AlertRuleEntity();
    ReflectionTestUtils.setField(existing, "id", 22L);
    existing.setName("old-name");
    existing.setType(AlertRuleType.PRICE_THRESHOLD);
    existing.setSeverity("MEDIUM");
    existing.setEnabled(true);
    existing.setConditionJson("{\"version\":1,\"type\":\"PRICE\",\"condition\":{\"symbol\":\"BTC-USDT\",\"op\":\"gte\",\"threshold\":\"100000\",\"cooldownSec\":0}}");

    when(alertRuleRepository.findById(22L)).thenReturn(Optional.of(existing));

    PriceRuleSpec spec = new PriceRuleSpec();
    spec.setVersion(1);
    spec.setType("PRICE");
    PriceRuleCondition condition = new PriceRuleCondition();
    condition.setSymbol("BTC-USDT");
    condition.setOp(PriceRuleOperator.GTE);
    condition.setThreshold("120000");
    condition.setCooldownSec(999999);
    spec.setCondition(condition);

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.update(new AlertRuleUpdateCommand(
      22L,
      "new-name",
      objectMapper.valueToTree(spec),
      "HIGH",
      true
    )));
    assertEquals("condition.cooldownSec must be <= 86400", ex.getMessage());
  }

  @Test
  void shouldRejectPatchWhenEventRuleContainsAddressField() {
    DefaultAlertRuleService service = buildService();

    AlertRuleEntity existing = new AlertRuleEntity();
    ReflectionTestUtils.setField(existing, "id", 23L);
    existing.setName("old-name");
    existing.setType(AlertRuleType.EVENT);
    existing.setSeverity("MEDIUM");
    existing.setEnabled(true);
    existing.setConditionJson("{\"version\":1,\"type\":\"EVENT\",\"condition\":{\"all\":[{\"field\":\"chain\",\"op\":\"eq\",\"value\":\"ETH\"}]}}");

    when(alertRuleRepository.findById(23L)).thenReturn(Optional.of(existing));

    EventRuleSpec spec = new EventRuleSpec(
      1,
      "EVENT",
      new EventRuleCondition(List.of(
        new EventRuleConditionItem(EventRuleField.CHAIN, EventRuleOperator.EQ, "ETH"),
        new EventRuleConditionItem(EventRuleField.FROM_ADDRESS, EventRuleOperator.EQ, "0xabc")
      ))
    );

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.patchCondition(new AlertRulePatchConditionCommand(
      23L,
      objectMapper.valueToTree(spec)
    )));
    assertEquals("Event rule must not contain address field: from_address", ex.getMessage());
  }

  @Test
  void shouldListRulesFilteredByType() {
    DefaultAlertRuleService service = buildService();

    AlertRuleEntity addressRule = new AlertRuleEntity();
    ReflectionTestUtils.setField(addressRule, "id", 31L);
    addressRule.setName("address-rule");
    addressRule.setType(AlertRuleType.EVENT);
    addressRule.setConditionJson("{}");
    addressRule.setSeverity("HIGH");
    addressRule.setEnabled(true);

    AlertRuleEntity priceRule = new AlertRuleEntity();
    ReflectionTestUtils.setField(priceRule, "id", 32L);
    priceRule.setName("price-rule");
    priceRule.setType(AlertRuleType.PRICE_THRESHOLD);
    priceRule.setConditionJson("{}");
    priceRule.setSeverity("HIGH");
    priceRule.setEnabled(true);

    when(alertRuleRepository.findAll()).thenReturn(List.of(addressRule, priceRule));

    List<AlertRuleView> results = service.list(new AlertRuleQueryCommand(AlertRuleType.PRICE_THRESHOLD, null, null));

    assertEquals(1, results.size());
    assertEquals(32L, results.get(0).id());
    assertEquals(AlertRuleType.PRICE_THRESHOLD, results.get(0).type());
  }

  @Test
  void shouldListRulesFilteredByEnabled() {
    DefaultAlertRuleService service = buildService();

    AlertRuleEntity enabledRule = new AlertRuleEntity();
    ReflectionTestUtils.setField(enabledRule, "id", 41L);
    enabledRule.setName("enabled-rule");
    enabledRule.setType(AlertRuleType.EVENT);
    enabledRule.setConditionJson("{}");
    enabledRule.setSeverity("HIGH");
    enabledRule.setEnabled(true);

    AlertRuleEntity disabledRule = new AlertRuleEntity();
    ReflectionTestUtils.setField(disabledRule, "id", 42L);
    disabledRule.setName("disabled-rule");
    disabledRule.setType(AlertRuleType.EVENT);
    disabledRule.setConditionJson("{}");
    disabledRule.setSeverity("HIGH");
    disabledRule.setEnabled(false);

    when(alertRuleRepository.findAll()).thenReturn(List.of(enabledRule, disabledRule));

    List<AlertRuleView> results = service.list(new AlertRuleQueryCommand(null, false, null));

    assertEquals(1, results.size());
    assertEquals(42L, results.get(0).id());
    assertEquals(false, results.get(0).enabled());
  }

  @Test
  void shouldSoftDeleteRuleSuccessfully() {
    DefaultAlertRuleService service = buildService();

    AlertRuleEntity existing = new AlertRuleEntity();
    ReflectionTestUtils.setField(existing, "id", 51L);
    existing.setName("delete-me");
    existing.setType(AlertRuleType.EVENT);
    existing.setConditionJson("{}");
    existing.setSeverity("HIGH");
    existing.setEnabled(true);

    when(alertRuleRepository.findById(51L)).thenReturn(Optional.of(existing));
    when(alertRuleRepository.save(any(AlertRuleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AlertRuleView view = service.delete(51L);

    assertEquals(51L, view.id());
    assertEquals(false, view.enabled());
    verify(alertRuleRepository).save(any(AlertRuleEntity.class));
  }

  @Test
  void shouldDeleteIdempotentlyWhenRuleAlreadyDisabled() {
    DefaultAlertRuleService service = buildService();

    AlertRuleEntity existing = new AlertRuleEntity();
    ReflectionTestUtils.setField(existing, "id", 52L);
    existing.setName("already-disabled");
    existing.setType(AlertRuleType.EVENT);
    existing.setConditionJson("{}");
    existing.setSeverity("HIGH");
    existing.setEnabled(false);

    when(alertRuleRepository.findById(52L)).thenReturn(Optional.of(existing));

    AlertRuleView view = service.delete(52L);

    assertEquals(52L, view.id());
    assertEquals(false, view.enabled());
    verify(alertRuleRepository, never()).save(any(AlertRuleEntity.class));
  }

  @Test
  void shouldThrowNotFoundWhenGetByIdMissing() {
    DefaultAlertRuleService service = buildService();
    when(alertRuleRepository.findById(999L)).thenReturn(Optional.empty());

    NotFoundException ex = assertThrows(NotFoundException.class, () -> service.getById(999L));
    assertEquals("Rule not found: 999", ex.getMessage());
  }

  @Test
  void shouldSetEnabledAndPersistWhenStatusChanges() {
    DefaultAlertRuleService service = buildService();

    AlertRuleEntity existing = new AlertRuleEntity();
    ReflectionTestUtils.setField(existing, "id", 61L);
    existing.setName("toggle-rule");
    existing.setType(AlertRuleType.EVENT);
    existing.setConditionJson("{}");
    existing.setSeverity("HIGH");
    existing.setEnabled(false);

    when(alertRuleRepository.findById(61L)).thenReturn(Optional.of(existing));
    when(alertRuleRepository.save(any(AlertRuleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AlertRuleView view = service.setEnabled(61L, true);

    assertEquals(61L, view.id());
    assertEquals(true, view.enabled());
    verify(alertRuleRepository).save(any(AlertRuleEntity.class));
  }

  @Test
  void shouldSetEnabledIdempotentlyWithoutPersistWhenStatusUnchanged() {
    DefaultAlertRuleService service = buildService();

    AlertRuleEntity existing = new AlertRuleEntity();
    ReflectionTestUtils.setField(existing, "id", 62L);
    existing.setName("toggle-rule-idempotent");
    existing.setType(AlertRuleType.EVENT);
    existing.setConditionJson("{}");
    existing.setSeverity("HIGH");
    existing.setEnabled(true);

    when(alertRuleRepository.findById(62L)).thenReturn(Optional.of(existing));

    AlertRuleView view = service.setEnabled(62L, true);

    assertEquals(62L, view.id());
    assertEquals(true, view.enabled());
    verify(alertRuleRepository, never()).save(any(AlertRuleEntity.class));
  }
}

