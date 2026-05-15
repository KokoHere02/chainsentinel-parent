package com.chainsentinel.marketgateway.api.support;

import com.chainsentinel.marketgateway.config.GatewayInternalTokenProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GatewayInternalTokenFilter extends OncePerRequestFilter {

	private static final String DEFAULT_HEADER_NAME = "X-Internal-Token";

	private final GatewayInternalTokenProperties properties;

	public GatewayInternalTokenFilter(GatewayInternalTokenProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (!properties.isEnabled()) {
			filterChain.doFilter(request, response);
			return;
		}
		if (isHealthProbe(request)) {
			filterChain.doFilter(request, response);
			return;
		}
		String expectedToken = properties.getToken();
		String actualToken = request.getHeader(resolveHeaderName());
		if (StringUtils.hasText(expectedToken) && tokenEquals(expectedToken, actualToken)) {
			filterChain.doFilter(request, response);
			return;
		}
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"status\":401,\"message\":\"unauthorized market gateway request\"}");
	}

	private boolean isHealthProbe(HttpServletRequest request) {
		return "GET".equalsIgnoreCase(request.getMethod()) && "/api/v1/market/health".equals(request.getRequestURI());
	}

	private String resolveHeaderName() {
		return StringUtils.hasText(properties.getHeaderName()) ? properties.getHeaderName().trim() : DEFAULT_HEADER_NAME;
	}

	private boolean tokenEquals(String expectedToken, String actualToken) {
		if (!StringUtils.hasText(actualToken)) {
			return false;
		}
		byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);
		byte[] actual = actualToken.getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(expected, actual);
	}
}
