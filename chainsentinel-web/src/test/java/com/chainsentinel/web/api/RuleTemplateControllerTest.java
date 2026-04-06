package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuleTemplateControllerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		RuleTemplateController controller = new RuleTemplateController();
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void shouldReturnBuiltInRuleTemplates() throws Exception {
		mockMvc.perform(get("/api/rule-templates"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()", is(3)))
			.andExpect(jsonPath("$[0].key", is("PRICE_BREAKOUT")))
			.andExpect(jsonPath("$[0].type", is("PRICE_THRESHOLD")))
			.andExpect(jsonPath("$[1].key", is("ADDRESS_LARGE_TRANSFER")))
			.andExpect(jsonPath("$[1].type", is("AMOUNT")))
			.andExpect(jsonPath("$[2].key", is("CONTRACT_INTERACTION")))
			.andExpect(jsonPath("$[2].type", is("ADDRESS")));
	}
}
