package com.chainsentinel.web.ws;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class PriceFrontendWebSocketConfig implements WebSocketConfigurer {

	private static final String DEFAULT_ALLOWED_ORIGIN = "http://localhost:5173";

	private final PriceFrontendWebSocketHandler priceFrontendWebSocketHandler;
	private final String[] allowedOrigins;

	public PriceFrontendWebSocketConfig(
		PriceFrontendWebSocketHandler priceFrontendWebSocketHandler,
		@Value("${chainsentinel.web.allowed-origins:http://localhost:5173}") String allowedOriginsValue
	) {
		this.priceFrontendWebSocketHandler = priceFrontendWebSocketHandler;
		this.allowedOrigins = parseAllowedOrigins(allowedOriginsValue);
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(priceFrontendWebSocketHandler, "/ws/price")
			.setAllowedOrigins(allowedOrigins);
	}

	private String[] parseAllowedOrigins(String value) {
		if (!StringUtils.hasText(value)) {
			return new String[] { DEFAULT_ALLOWED_ORIGIN };
		}
		String[] parsed = StringUtils.commaDelimitedListToStringArray(value);
		String[] normalized = Arrays.stream(parsed)
			.map(String::trim)
			.filter(StringUtils::hasText)
			.toArray(String[]::new);
		if (normalized.length == 0) {
			return new String[] { DEFAULT_ALLOWED_ORIGIN };
		}
		return normalized;
	}
}
