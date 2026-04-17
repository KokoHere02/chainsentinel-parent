package com.chainsentinel.web.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class PriceFrontendWebSocketConfig implements WebSocketConfigurer {

	private final PriceFrontendWebSocketHandler priceFrontendWebSocketHandler;

	public PriceFrontendWebSocketConfig(PriceFrontendWebSocketHandler priceFrontendWebSocketHandler) {
		this.priceFrontendWebSocketHandler = priceFrontendWebSocketHandler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(priceFrontendWebSocketHandler, "/ws/price")
			.setAllowedOrigins("http://localhost:5173");
	}
}

