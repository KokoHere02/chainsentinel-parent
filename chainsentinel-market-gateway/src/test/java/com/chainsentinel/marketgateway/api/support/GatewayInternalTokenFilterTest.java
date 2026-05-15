package com.chainsentinel.marketgateway.api.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.chainsentinel.marketgateway.config.GatewayInternalTokenProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayInternalTokenFilterTest {

	@Test
	void shouldPassThroughWhenDisabled() throws Exception {
		GatewayInternalTokenProperties properties = new GatewayInternalTokenProperties();
		GatewayInternalTokenFilter filter = new GatewayInternalTokenFilter(properties);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market/providers");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> response.setStatus(204));

		assertEquals(204, response.getStatus());
	}

	@Test
	void shouldAllowHealthProbeWithoutToken() throws Exception {
		GatewayInternalTokenProperties properties = enabledProperties();
		GatewayInternalTokenFilter filter = new GatewayInternalTokenFilter(properties);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market/health");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> response.setStatus(204));

		assertEquals(204, response.getStatus());
	}

	@Test
	void shouldRejectMissingTokenWhenEnabled() throws Exception {
		GatewayInternalTokenProperties properties = enabledProperties();
		GatewayInternalTokenFilter filter = new GatewayInternalTokenFilter(properties);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market/providers");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> response.setStatus(204));

		assertEquals(401, response.getStatus());
	}

	@Test
	void shouldAcceptMatchingTokenWhenEnabled() throws Exception {
		GatewayInternalTokenProperties properties = enabledProperties();
		GatewayInternalTokenFilter filter = new GatewayInternalTokenFilter(properties);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market/providers");
		request.addHeader("X-Internal-Token", "secret");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> response.setStatus(204));

		assertEquals(204, response.getStatus());
	}

	private GatewayInternalTokenProperties enabledProperties() {
		GatewayInternalTokenProperties properties = new GatewayInternalTokenProperties();
		properties.setEnabled(true);
		properties.setToken("secret");
		return properties;
	}
}
