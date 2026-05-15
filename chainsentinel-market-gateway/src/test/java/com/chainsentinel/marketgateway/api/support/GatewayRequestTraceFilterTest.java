package com.chainsentinel.marketgateway.api.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayRequestTraceFilterTest {

	private final GatewayRequestTraceFilter filter = new GatewayRequestTraceFilter();

	@Test
	void shouldUseRequestIdHeader() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market/health");
		request.addHeader(GatewayRequestTraceFilter.HEADER_REQUEST_ID, "trace-1");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, assertingChain("trace-1"));

		assertEquals("trace-1", response.getHeader(GatewayRequestTraceFilter.HEADER_REQUEST_ID));
		assertEquals("trace-1", request.getAttribute(GatewayRequestTraceFilter.REQUEST_ATTR_REQUEST_ID));
		assertNull(MDC.get(GatewayRequestTraceFilter.MDC_REQUEST_ID));
	}

	@Test
	void shouldFallbackToCorrelationIdHeader() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market/health");
		request.addHeader(GatewayRequestTraceFilter.HEADER_CORRELATION_ID, "corr-1");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, assertingChain("corr-1"));

		assertEquals("corr-1", response.getHeader(GatewayRequestTraceFilter.HEADER_REQUEST_ID));
		assertEquals("corr-1", request.getAttribute(GatewayRequestTraceFilter.REQUEST_ATTR_REQUEST_ID));
	}

	@Test
	void shouldGenerateRequestIdWhenHeadersMissing() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market/health");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> {
			Object requestId = servletRequest.getAttribute(GatewayRequestTraceFilter.REQUEST_ATTR_REQUEST_ID);
			assertNotNull(requestId);
			assertEquals(requestId, MDC.get(GatewayRequestTraceFilter.MDC_REQUEST_ID));
		});

		assertNotNull(response.getHeader(GatewayRequestTraceFilter.HEADER_REQUEST_ID));
		assertNull(MDC.get(GatewayRequestTraceFilter.MDC_REQUEST_ID));
	}

	private FilterChain assertingChain(String expectedRequestId) {
		return (servletRequest, servletResponse) -> {
			assertEquals(expectedRequestId, servletRequest.getAttribute(GatewayRequestTraceFilter.REQUEST_ATTR_REQUEST_ID));
			assertEquals(expectedRequestId, MDC.get(GatewayRequestTraceFilter.MDC_REQUEST_ID));
		};
	}
}
