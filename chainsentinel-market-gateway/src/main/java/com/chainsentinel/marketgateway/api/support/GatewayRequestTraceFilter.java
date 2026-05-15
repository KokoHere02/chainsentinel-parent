package com.chainsentinel.marketgateway.api.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayRequestTraceFilter extends OncePerRequestFilter {

	public static final String HEADER_REQUEST_ID = "X-Request-Id";
	public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
	public static final String MDC_REQUEST_ID = "requestId";
	public static final String REQUEST_ATTR_REQUEST_ID = "requestId";

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		String requestId = resolveRequestId(request);
		MDC.put(MDC_REQUEST_ID, requestId);
		request.setAttribute(REQUEST_ATTR_REQUEST_ID, requestId);
		response.setHeader(HEADER_REQUEST_ID, requestId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_REQUEST_ID);
		}
	}

	private String resolveRequestId(HttpServletRequest request) {
		String requestId = normalize(request.getHeader(HEADER_REQUEST_ID));
		if (requestId != null) {
			return requestId;
		}
		String correlationId = normalize(request.getHeader(HEADER_CORRELATION_ID));
		if (correlationId != null) {
			return correlationId;
		}
		return UUID.randomUUID().toString().replace("-", "");
	}

	private String normalize(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
	}
}
