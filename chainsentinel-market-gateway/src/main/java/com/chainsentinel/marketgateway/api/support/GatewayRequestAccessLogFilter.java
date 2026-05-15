package com.chainsentinel.marketgateway.api.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class GatewayRequestAccessLogFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(GatewayRequestAccessLogFilter.class);

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		long startedAt = System.currentTimeMillis();
		try {
			filterChain.doFilter(request, response);
		} finally {
			long durationMs = System.currentTimeMillis() - startedAt;
			Object requestId = request.getAttribute(GatewayRequestTraceFilter.REQUEST_ATTR_REQUEST_ID);
			log.info(
				"market.gateway.access traceId={} method={} path={} status={} durationMs={} remoteAddr={}",
				requestId == null ? "-" : requestId,
				request.getMethod(),
				request.getRequestURI(),
				response.getStatus(),
				durationMs,
				maskIp(request.getRemoteAddr())
			);
		}
	}

	private String maskIp(String remoteAddr) {
		if (remoteAddr == null || remoteAddr.isBlank()) {
			return "-";
		}
		int lastDot = remoteAddr.lastIndexOf('.');
		if (lastDot > 0) {
			return remoteAddr.substring(0, lastDot + 1) + "*";
		}
		int lastColon = remoteAddr.lastIndexOf(':');
		if (lastColon > 0) {
			return remoteAddr.substring(0, lastColon + 1) + "*";
		}
		return "*";
	}
}
