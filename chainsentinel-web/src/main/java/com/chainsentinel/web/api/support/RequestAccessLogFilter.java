package com.chainsentinel.web.api.support;

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
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestAccessLogFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestAccessLogFilter.class);

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
			Object requestId = request.getAttribute(RequestTraceFilter.REQUEST_ATTR_REQUEST_ID);
			log.info(
				"api.access traceId={} method={} path={} status={} durationMs={} remoteAddr={}",
				requestId == null ? "-" : requestId,
				request.getMethod(),
				request.getRequestURI(),
				response.getStatus(),
				durationMs,
				LogSanitizer.maskIp(request.getRemoteAddr())
			);
		}
	}
}
