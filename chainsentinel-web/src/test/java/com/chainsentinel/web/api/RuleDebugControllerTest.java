package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.core.service.AlertRuleService;
import com.chainsentinel.core.service.dto.AlertRuleView;
import com.chainsentinel.infra.rule.EventRuleConditionParser;
import com.chainsentinel.infra.rule.PriceRuleConditionParser;
import com.chainsentinel.infra.rule.RuleConditionJsonParser;
import com.chainsentinel.web.api.support.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RuleDebugControllerTest {

	@Mock
	private AlertRuleService alertRuleService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		ObjectMapper objectMapper = new ObjectMapper();
		EventRuleConditionParser eventRuleConditionParser = new EventRuleConditionParser(objectMapper);
		PriceRuleConditionParser priceRuleConditionParser = new PriceRuleConditionParser(objectMapper);
		RuleConditionJsonParser ruleConditionJsonParser = new RuleConditionJsonParser(
			objectMapper,
			eventRuleConditionParser,
			priceRuleConditionParser
		);
		RuleDebugController controller = new RuleDebugController(
			alertRuleService,
			ruleConditionJsonParser,
			new SimpleMeterRegistry(),
			true
		);
		mockMvc = MockMvcBuilders
			.standaloneSetup(controller)
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldMatchPriceRuleWithSamplePrice() throws Exception {
		when(alertRuleService.getById(100L))
			.thenReturn(new AlertRuleView(
				100L,
				"price-rule",
				AlertRuleType.PRICE_THRESHOLD,
				"""
				{"version":1,"type":"PRICE","condition":{"symbol":"BTC-USDT","op":"gte","threshold":"100000","cooldownSec":0}}
				""",
				"HIGH",
				true
			));

		mockMvc.perform(post("/api/rules/100/test-match")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "sample": {
					    "currentPrice": "120000"
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matched", is(true)))
			.andExpect(jsonPath("$.reason", is("matched")))
			.andExpect(jsonPath("$.reasonDetail", containsString("price condition matched")))
			.andExpect(jsonPath("$.passedConditions.length()", is(1)))
			.andExpect(jsonPath("$.passedConditions[0].field", is("price")))
			.andExpect(jsonPath("$.failedCondition", nullValue()));
	}

	@Test
	void shouldReturnPriceMismatchReasonDetail() throws Exception {
		when(alertRuleService.getById(102L))
			.thenReturn(new AlertRuleView(
				102L,
				"price-rule-mismatch",
				AlertRuleType.PRICE_THRESHOLD,
				"""
				{"version":1,"type":"PRICE","condition":{"symbol":"BTC-USDT","op":"gte","threshold":"100000","cooldownSec":0}}
				""",
				"HIGH",
				true
			));

		mockMvc.perform(post("/api/rules/102/test-match")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "sample": {
					    "currentPrice": "99999"
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matched", is(false)))
			.andExpect(jsonPath("$.reason", is("not_matched")))
			.andExpect(jsonPath("$.reasonDetail", containsString("price condition not matched")))
			.andExpect(jsonPath("$.passedConditions.length()", is(0)))
			.andExpect(jsonPath("$.failedCondition.field", is("price")))
			.andExpect(jsonPath("$.failedCondition.op", is("gte")));
	}

	@Test
	void shouldNotMatchEventRuleWithSampleEvent() throws Exception {
		when(alertRuleService.getById(101L))
			.thenReturn(new AlertRuleView(
				101L,
				"address-rule",
				AlertRuleType.EVENT,
				"""
				{"version":1,"type":"EVENT","condition":{"all":[{"field":"chain","op":"eq","value":"ETH"},{"field":"to_address","op":"eq","value":"0xabc"}]}}
				""",
				"HIGH",
				true
			));

		mockMvc.perform(post("/api/rules/101/test-match")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "sample": {
					    "chain": "ETH",
					    "to_address": "0xdef"
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matched", is(false)))
			.andExpect(jsonPath("$.reason", is("not_matched")))
			.andExpect(jsonPath("$.reasonDetail", containsString("field=to_address")))
			.andExpect(jsonPath("$.passedConditions.length()", is(1)))
			.andExpect(jsonPath("$.passedConditions[0].field", is("chain")))
			.andExpect(jsonPath("$.failedCondition.field", is("to_address")));
	}

	@Test
	void shouldReturn404WhenTestMatchDisabled() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		EventRuleConditionParser eventRuleConditionParser = new EventRuleConditionParser(objectMapper);
		PriceRuleConditionParser priceRuleConditionParser = new PriceRuleConditionParser(objectMapper);
		RuleConditionJsonParser ruleConditionJsonParser = new RuleConditionJsonParser(
			objectMapper,
			eventRuleConditionParser,
			priceRuleConditionParser
		);
		RuleDebugController disabledController = new RuleDebugController(
			alertRuleService,
			ruleConditionJsonParser,
			new SimpleMeterRegistry(),
			false
		);
		MockMvc disabledMvc = MockMvcBuilders
			.standaloneSetup(disabledController)
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

		disabledMvc.perform(post("/api/rules/100/test-match")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "sample": {
					    "currentPrice": "120000"
					  }
					}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code", is("DEBUG_ENDPOINT_DISABLED")));

		verifyNoInteractions(alertRuleService);
	}

	@Test
	void shouldBatchTestMatchWithMixedResults() throws Exception {
		when(alertRuleService.getById(103L))
			.thenReturn(new AlertRuleView(
				103L,
				"price-rule-batch",
				AlertRuleType.PRICE_THRESHOLD,
				"""
				{"version":1,"type":"PRICE","condition":{"symbol":"BTC-USDT","op":"gte","threshold":"100000","cooldownSec":0}}
				""",
				"HIGH",
				true
			));

		mockMvc.perform(post("/api/rules/103/test-match/batch")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "samples": [
					    {"currentPrice": "120000"},
					    {"currentPrice": "80000"}
					  ]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].index", is(0)))
			.andExpect(jsonPath("$[0].result.matched", is(true)))
			.andExpect(jsonPath("$[1].index", is(1)))
			.andExpect(jsonPath("$[1].result.matched", is(false)))
			.andExpect(jsonPath("$[1].result.failedCondition.field", is("price")));
	}

	@Test
	void shouldBatchTestMatchReturnOnlyFailedWhenRequested() throws Exception {
		when(alertRuleService.getById(104L))
			.thenReturn(new AlertRuleView(
				104L,
				"price-rule-only-failed",
				AlertRuleType.PRICE_THRESHOLD,
				"""
				{"version":1,"type":"PRICE","condition":{"symbol":"BTC-USDT","op":"gte","threshold":"100000","cooldownSec":0}}
				""",
				"HIGH",
				true
			));

		mockMvc.perform(post("/api/rules/104/test-match/batch")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "onlyFailed": true,
					  "samples": [
					    {"currentPrice": "120000"},
					    {"currentPrice": "80000"}
					  ]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()", is(1)))
			.andExpect(jsonPath("$[0].index", is(1)))
			.andExpect(jsonPath("$[0].result.matched", is(false)))
			.andExpect(jsonPath("$[0].result.failedCondition.field", is("price")));
	}

	@Test
	void shouldReturn400WhenBatchSamplesEmpty() throws Exception {
		mockMvc.perform(post("/api/rules/103/test-match/batch")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "samples": []
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
	}

	@Test
	void shouldReturn400WhenBatchSamplesExceedMax() throws Exception {
		StringBuilder samples = new StringBuilder();
		samples.append("[");
		for (int i = 0; i < 101; i++) {
			if (i > 0) {
				samples.append(",");
			}
			samples.append("{\"currentPrice\":\"100000\"}");
		}
		samples.append("]");

		mockMvc.perform(post("/api/rules/103/test-match/batch")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"samples\":" + samples + "}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
	}
}

