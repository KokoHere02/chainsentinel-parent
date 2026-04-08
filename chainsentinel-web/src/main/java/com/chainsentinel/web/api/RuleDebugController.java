package com.chainsentinel.web.api;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.model.TokenType;
import com.chainsentinel.core.exception.DebugEndpointDisabledException;
import com.chainsentinel.core.rule.model.EventRuleConditionItem;
import com.chainsentinel.core.rule.model.EventRuleField;
import com.chainsentinel.core.rule.model.EventRuleOperator;
import com.chainsentinel.core.rule.model.EventRuleSpec;
import com.chainsentinel.core.rule.model.PriceRuleSpec;
import com.chainsentinel.core.service.AlertRuleService;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.rule.AmountComparisonValueConverter;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
import com.chainsentinel.infra.rule.RuleConditionJsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
@Validated
public class RuleDebugController {

	private static final Logger log = LoggerFactory.getLogger(RuleDebugController.class);
	private static final String METRIC_RULE_TEST_MATCH_TOTAL = "rule_test_match_total";
	private static final String METRIC_RULE_TEST_MATCH_FAIL_TOTAL = "rule_test_match_fail_total";
	private static final String METRIC_RULE_TEST_MATCH_LATENCY = "rule_test_match_latency";

	private final AlertRuleService alertRuleService;
	private final RuleConditionJsonParser ruleConditionJsonParser;
	private final MeterRegistry meterRegistry;
	private final boolean testMatchEnabled;

	public RuleDebugController(
		AlertRuleService alertRuleService,
		RuleConditionJsonParser ruleConditionJsonParser,
		MeterRegistry meterRegistry,
		@Value("${chainsentinel.debug.rule-test-match-enabled:false}") boolean testMatchEnabled
	) {
		this.alertRuleService = alertRuleService;
		this.ruleConditionJsonParser = ruleConditionJsonParser;
		this.meterRegistry = meterRegistry;
		this.testMatchEnabled = testMatchEnabled;
	}

	@PostMapping("/{id}/test-match")
	public RuleTestMatchResponse testMatch(@PathVariable Long id, @RequestBody @Valid RuleTestMatchRequest request) {
		Timer.Sample timerSample = Timer.start(meterRegistry);
		log.info("rule.test_match.request ruleId={} mode=single samples=1", id);
		try {
			AlertRuleView rule = requireEnabledAndGetRule(id);
			RuleTestMatchResponse response = toResponse(evaluate(rule, request.sample()));
			recordSuccess("single");
			return response;
		} catch (RuntimeException ex) {
			recordFailure("single", ex);
			throw ex;
		} finally {
			recordLatency("single", timerSample);
		}
	}

	@PostMapping("/{id}/test-match/batch")
	public List<BatchRuleTestMatchItem> testMatchBatch(
		@PathVariable Long id,
		@RequestBody @Valid RuleTestMatchBatchRequest request
	) {
		Timer.Sample timerSample = Timer.start(meterRegistry);
		log.info("rule.test_match.request ruleId={} mode=batch samples={}", id, request.samples().size());
		try {
			AlertRuleView rule = requireEnabledAndGetRule(id);
			List<BatchRuleTestMatchItem> results = new ArrayList<>();
			for (int i = 0; i < request.samples().size(); i++) {
				JsonNode sample = request.samples().get(i);
				MatchDetail detail = evaluate(rule, sample);
				if (Boolean.TRUE.equals(request.onlyFailed()) && detail.matched()) {
					continue;
				}
				results.add(new BatchRuleTestMatchItem(i, toResponse(detail)));
			}
			recordSuccess("batch");
			return results;
		} catch (RuntimeException ex) {
			recordFailure("batch", ex);
			throw ex;
		} finally {
			recordLatency("batch", timerSample);
		}
	}

	private void recordSuccess(String mode) {
		meterRegistry.counter(METRIC_RULE_TEST_MATCH_TOTAL, "mode", mode, "status", "success").increment();
	}

	private void recordFailure(String mode, RuntimeException ex) {
		String error = ex.getClass().getSimpleName();
		meterRegistry.counter(METRIC_RULE_TEST_MATCH_TOTAL, "mode", mode, "status", "error").increment();
		meterRegistry.counter(METRIC_RULE_TEST_MATCH_FAIL_TOTAL, "mode", mode, "error", error).increment();
	}

	private void recordLatency(String mode, Timer.Sample timerSample) {
		timerSample.stop(meterRegistry.timer(METRIC_RULE_TEST_MATCH_LATENCY, "mode", mode));
	}

	private AlertRuleView requireEnabledAndGetRule(Long id) {
		if (!testMatchEnabled) {
			throw new DebugEndpointDisabledException();
		}
		return alertRuleService.getById(id);
	}

	private MatchDetail evaluate(AlertRuleView rule, JsonNode sample) {
		return switch (rule.type()) {
			case PRICE_THRESHOLD -> matchPriceRule(rule, sample);
			case ADDRESS, AMOUNT -> matchEventRule(rule, sample);
			default -> throw new IllegalArgumentException("Unsupported rule type: " + rule.type());
		};
	}

	private RuleTestMatchResponse toResponse(MatchDetail detail) {
		return new RuleTestMatchResponse(
			detail.matched(),
			detail.matched() ? "matched" : "not_matched",
			detail.reasonDetail(),
			detail.passedConditions(),
			detail.failedCondition()
		);
	}

	private MatchDetail matchPriceRule(AlertRuleView rule, JsonNode sample) {
		PriceRuleSpec spec = ruleConditionJsonParser.parsePrice(rule.conditionJson());
		JsonNode currentPriceNode = sample.get("currentPrice");
		if (currentPriceNode == null || currentPriceNode.isNull()) {
			throw new IllegalArgumentException("sample.currentPrice is required for PRICE_THRESHOLD");
		}
		BigDecimal currentPrice = new BigDecimal(currentPriceNode.asText());
		BigDecimal threshold = new BigDecimal(spec.getCondition().getThreshold());
		String op = spec.getCondition().getOp().wireValue();
		boolean matched = ruleConditionJsonParser.matchPrice(spec, currentPrice);
		String reasonDetail = matched
			? "price condition matched: currentPrice=%s op=%s threshold=%s".formatted(currentPrice, op, threshold)
			: "price condition not matched: currentPrice=%s op=%s threshold=%s".formatted(currentPrice, op, threshold);
		ConditionResult result = new ConditionResult("price", op, threshold.toPlainString(), currentPrice.toPlainString(), matched);
		return matched
			? new MatchDetail(true, reasonDetail, List.of(result), null)
			: new MatchDetail(false, reasonDetail, List.of(), result);
	}

	private MatchDetail matchEventRule(AlertRuleView rule, JsonNode sample) {
		AssetEventEntity event = toSampleEvent(sample);
		EventRuleSpec spec = ruleConditionJsonParser.parseEvent(rule.conditionJson());
		List<ConditionResult> passed = new ArrayList<>();
		for (EventRuleConditionItem item : spec.getCondition().getAll()) {
			ConditionResult detail = evalEventCondition(item, event);
			if (!detail.matched()) {
				return new MatchDetail(
					false,
					"condition failed: field=%s op=%s expected=%s actual=%s"
						.formatted(detail.field(), detail.op(), detail.expected(), detail.actual()),
					passed,
					detail
				);
			}
			passed.add(detail);
		}
		return new MatchDetail(true, "all event conditions matched", passed, null);
	}

	private ConditionResult evalEventCondition(EventRuleConditionItem item, AssetEventEntity event) {
		Object actual = fieldValue(item.getField(), event);
		EventRuleOperator op = item.getOp();
		Object expected = item.getValue();
		boolean matched;

		if (op == EventRuleOperator.IN || op == EventRuleOperator.NOT_IN) {
			List<?> values = toList(expected);
			boolean contains = containsByField(item.getField(), actual, values);
			matched = (op == EventRuleOperator.IN) == contains;
		} else if (op == EventRuleOperator.EQ || op == EventRuleOperator.NE) {
			boolean equal = equalsByField(item.getField(), actual, expected);
			matched = (op == EventRuleOperator.EQ) == equal;
		} else {
			int cmp = compareByField(item.getField(), actual, expected);
			matched = switch (op) {
				case GT -> cmp > 0;
				case GTE -> cmp >= 0;
				case LT -> cmp < 0;
				case LTE -> cmp <= 0;
				default -> throw new IllegalArgumentException("Unsupported op: " + op.wireValue());
			};
		}

		return new ConditionResult(
			item.getField().wireValue(),
			op.wireValue(),
			String.valueOf(expected),
			String.valueOf(actual),
			matched
		);
	}

	private Object fieldValue(EventRuleField field, AssetEventEntity event) {
		return switch (field) {
			case CHAIN -> event.getChain();
			case NETWORK -> event.getNetwork();
			case FROM_ADDRESS -> event.getFromAddress();
			case TO_ADDRESS -> event.getToAddress();
			case TOKEN_TYPE -> event.getTokenType() == null ? null : event.getTokenType().name();
			case TOKEN_CONTRACT -> event.getTokenContract();
			case SYMBOL -> event.getSymbol();
			case AMOUNT -> event.getAmount();
			case STATUS -> event.getStatus() == null ? null : event.getStatus().name();
		};
	}

	private boolean containsByField(EventRuleField field, Object left, List<?> values) {
		if (field == EventRuleField.AMOUNT) {
			BigInteger actual = AmountComparisonValueConverter.toComparisonValue(left);
			return values.stream()
				.map(AmountComparisonValueConverter::toComparisonValue)
				.anyMatch(actual::equals);
		}
		String actual = normalizeText(field, toText(left));
		return values.stream().map(v -> normalizeText(field, toText(v))).anyMatch(actual::equals);
	}

	private boolean equalsByField(EventRuleField field, Object left, Object right) {
		if (field == EventRuleField.AMOUNT) {
			return AmountComparisonValueConverter.toComparisonValue(left)
				.compareTo(AmountComparisonValueConverter.toComparisonValue(right)) == 0;
		}
		return normalizeText(field, toText(left)).equals(normalizeText(field, toText(right)));
	}

	private int compareByField(EventRuleField field, Object left, Object right) {
		if (field == EventRuleField.AMOUNT) {
			return AmountComparisonValueConverter.toComparisonValue(left)
				.compareTo(AmountComparisonValueConverter.toComparisonValue(right));
		}
		return toDecimal(left).compareTo(toDecimal(right));
	}

	private List<?> toList(Object value) {
		if (value instanceof List<?> list) {
			return list;
		}
		if (value instanceof Collection<?> collection) {
			return collection.stream().toList();
		}
		if (value != null && value.getClass().isArray()) {
			return Arrays.asList((Object[]) value);
		}
		throw new IllegalArgumentException("value is not array");
	}

	private BigDecimal toDecimal(Object value) {
		if (value == null) {
			return BigDecimal.ZERO;
		}
		return new BigDecimal(String.valueOf(value));
	}

	private String normalizeText(EventRuleField field, String value) {
		if (value == null) {
			return "";
		}
		String normalized = value.trim();
		if (
			field == EventRuleField.FROM_ADDRESS ||
			field == EventRuleField.TO_ADDRESS ||
			field == EventRuleField.TOKEN_CONTRACT
		) {
			return normalized.toLowerCase(Locale.ROOT);
		}
		return normalized;
	}

	private String toText(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private AssetEventEntity toSampleEvent(JsonNode sample) {
		AssetEventEntity event = new AssetEventEntity();
		event.setChain(readText(sample, "chain"));
		event.setNetwork(readText(sample, "network"));
		event.setFromAddress(readText(sample, "fromAddress", "from_address"));
		event.setToAddress(readText(sample, "toAddress", "to_address"));
		event.setTokenContract(readText(sample, "tokenContract", "token_contract"));
		event.setSymbol(readText(sample, "symbol"));
		event.setAmount(readText(sample, "amount"));
		event.setTokenType(readEnum(sample, TokenType.class, "tokenType", "token_type"));
		event.setStatus(readEnum(sample, EventStatus.class, "status"));
		event.setOccurredAt(Instant.now());
		event.setIngestedAt(Instant.now());
		return event;
	}

	private String readText(JsonNode sample, String... keys) {
		for (String key : keys) {
			JsonNode node = sample.get(key);
			if (node != null && !node.isNull()) {
				return node.asText();
			}
		}
		return null;
	}

	private <E extends Enum<E>> E readEnum(JsonNode sample, Class<E> enumType, String... keys) {
		String text = readText(sample, keys);
		if (text == null || text.isBlank()) {
			return null;
		}
		return Enum.valueOf(enumType, text.trim().toUpperCase());
	}

	public record RuleTestMatchRequest(@NotNull JsonNode sample) {
	}

	public record RuleTestMatchBatchRequest(
		@NotNull @Size(min = 1, max = 100) List<@NotNull JsonNode> samples,
		Boolean onlyFailed
	) {
		public RuleTestMatchBatchRequest {
			if (onlyFailed == null) {
				onlyFailed = false;
			}
		}
	}

	public record RuleTestMatchResponse(
		boolean matched,
		String reason,
		String reasonDetail,
		List<ConditionResult> passedConditions,
		ConditionResult failedCondition
	) {
	}

	public record ConditionResult(
		String field,
		String op,
		String expected,
		String actual,
		boolean matched
	) {
	}

	public record BatchRuleTestMatchItem(
		int index,
		RuleTestMatchResponse result
	) {
	}

	private record MatchDetail(
		boolean matched,
		String reasonDetail,
		List<ConditionResult> passedConditions,
		ConditionResult failedCondition
	) {
	}
}
