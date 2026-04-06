package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
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
			eventRuleConditionParser
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
			.andExpect(jsonPath("$.reasonDetail", containsString("price condition matched")));
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
			.andExpect(jsonPath("$.reasonDetail", containsString("price condition not matched")));
	}

	@Test
	void shouldNotMatchEventRuleWithSampleEvent() throws Exception {
		when(alertRuleService.getById(101L))
			.thenReturn(new AlertRuleView(
				101L,
				"address-rule",
				AlertRuleType.ADDRESS,
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
			.andExpect(jsonPath("$.reasonDetail", containsString("field=to_address")));
	}
}
