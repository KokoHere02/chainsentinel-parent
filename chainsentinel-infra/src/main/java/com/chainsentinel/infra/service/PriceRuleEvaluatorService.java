package com.chainsentinel.infra.service;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.rule.model.PriceRuleSpec;
import com.chainsentinel.infra.entity.AlertEventEntity;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.entity.AssetPriceSnapshotEntity;
import com.chainsentinel.infra.entity.RuleTriggerStateEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.repository.AssetPriceSnapshotRepository;
import com.chainsentinel.infra.repository.RuleTriggerStateRepository;
import com.chainsentinel.infra.rule.RuleConditionJsonParser;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceRuleEvaluatorService {

private static final Logger log = LoggerFactory.getLogger(PriceRuleEvaluatorService.class);

private static final String SEND_STATUS_PENDING = "PENDING";
private static final String METRIC_RULE_EVAL_FAIL_TOTAL = "rule_eval_fail_total";
private static final String METRIC_RULE_COOLDOWN_BLOCK_TOTAL = "rule_cooldown_block_total";

private final AlertRuleRepository alertRuleRepository;
private final AlertEventRepository alertEventRepository;
private final AssetPriceSnapshotRepository assetPriceSnapshotRepository;
private final RuleTriggerStateRepository ruleTriggerStateRepository;
private final RuleConditionJsonParser ruleConditionJsonParser;
private final MeterRegistry meterRegistry;

public PriceRuleEvaluatorService(
AlertRuleRepository alertRuleRepository,
AlertEventRepository alertEventRepository,
AssetPriceSnapshotRepository assetPriceSnapshotRepository,
RuleTriggerStateRepository ruleTriggerStateRepository,
RuleConditionJsonParser ruleConditionJsonParser,
MeterRegistry meterRegistry
) {
this.alertRuleRepository = alertRuleRepository;
this.alertEventRepository = alertEventRepository;
this.assetPriceSnapshotRepository = assetPriceSnapshotRepository;
this.ruleTriggerStateRepository = ruleTriggerStateRepository;
this.ruleConditionJsonParser = ruleConditionJsonParser;
this.meterRegistry = meterRegistry;
}

@Transactional
public int evaluateOnce() {
List<AlertRuleEntity> rules = alertRuleRepository.findByTypeAndEnabledTrue(AlertRuleType.PRICE_THRESHOLD);
int created = 0;
for (AlertRuleEntity rule : rules) {
try {
if (evaluateOneRule(rule)) {
created++;
}
} catch (IllegalArgumentException ex) {
recordRuleEvalFail(rule, "invalid");
log.warn("rule.eval.invalid ruleId={} type={} error={}",
rule.getId(),
rule.getType(),
ex.getMessage());
} catch (Exception ex) {
recordRuleEvalFail(rule, "error");
log.error("rule.eval.error ruleId={} type={} error={}",
rule.getId(),
rule.getType(),
ex.getMessage(),
ex);
}
}
return created;
}

private boolean evaluateOneRule(AlertRuleEntity rule) {
PriceRuleSpec spec = ruleConditionJsonParser.parsePrice(rule.getConditionJson());
String targetKey = spec.getCondition().getSymbol().trim().toUpperCase(Locale.ROOT);
Optional<AssetPriceSnapshotEntity> snapshotOpt = assetPriceSnapshotRepository.findTopByInstIdOrderByBucketTsDesc(targetKey);
if (snapshotOpt.isEmpty()) {
log.debug("price.rule.skip_no_snapshot ruleId={} targetKey={}", rule.getId(), targetKey);
return false;
}

AssetPriceSnapshotEntity snapshot = snapshotOpt.get();
BigDecimal currentPrice = snapshot.getPrice();
boolean matched = ruleConditionJsonParser.matchPrice(spec, currentPrice);

RuleTriggerStateEntity state = ruleTriggerStateRepository
.findByRuleIdAndTargetKey(rule.getId(), targetKey)
.orElseGet(() -> initState(rule.getId(), targetKey));

boolean active = Boolean.TRUE.equals(state.getActive());
state.setLastValue(currentPrice);

if (matched && !active) {
if (isInCooldown(spec, state)) {
ruleTriggerStateRepository.save(state);
recordCooldownBlock(rule);
log.info("price.rule.cooldown_skip ruleId={} targetKey={} cooldownSec={} lastTriggeredAt={}",
rule.getId(),
targetKey,
spec.getCondition().getCooldownSec(),
state.getLastTriggeredAt());
return false;
}
createAlert(rule);
state.setActive(true);
state.setLastTriggeredAt(Instant.now());
ruleTriggerStateRepository.save(state);
log.info("price.rule.triggered ruleId={} targetKey={} price={}", rule.getId(), targetKey, currentPrice);
return true;
}

if (!matched && active) {
state.setActive(false);
ruleTriggerStateRepository.save(state);
log.info("price.rule.reset ruleId={} targetKey={} price={}", rule.getId(), targetKey, currentPrice);
return false;
}

if (state.getId() == null) {
ruleTriggerStateRepository.save(state);
}
return false;
}

private RuleTriggerStateEntity initState(Long ruleId, String targetKey) {
RuleTriggerStateEntity state = new RuleTriggerStateEntity();
state.setRuleId(ruleId);
state.setTargetKey(targetKey);
state.setActive(false);
return state;
}

private void createAlert(AlertRuleEntity rule) {
AlertEventEntity alert = new AlertEventEntity();
alert.setRuleId(rule.getId());
alert.setAssetEventId(null);
alert.setSeverity(rule.getSeverity());
alert.setSendStatus(SEND_STATUS_PENDING);
alert.setRetryCount(0);
alertEventRepository.save(alert);
}

private void recordRuleEvalFail(AlertRuleEntity rule, String reason) {
meterRegistry.counter(
METRIC_RULE_EVAL_FAIL_TOTAL,
"ruleId", String.valueOf(rule.getId()),
"type", String.valueOf(rule.getType()),
"reason", reason
).increment();
}

private boolean isInCooldown(PriceRuleSpec spec, RuleTriggerStateEntity state) {
Integer cooldownSec = spec.getCondition().getCooldownSec();
if (cooldownSec == null || cooldownSec <= 0) {
return false;
}
Instant lastTriggeredAt = state.getLastTriggeredAt();
if (lastTriggeredAt == null) {
return false;
}
return lastTriggeredAt.plusSeconds(cooldownSec).isAfter(Instant.now());
}

private void recordCooldownBlock(AlertRuleEntity rule) {
meterRegistry.counter(
METRIC_RULE_COOLDOWN_BLOCK_TOTAL,
"ruleId", String.valueOf(rule.getId()),
"type", String.valueOf(rule.getType())
).increment();
}
}
