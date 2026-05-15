package com.chainsentinel.marketgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.chainsentinel")
@ConfigurationPropertiesScan(basePackages = "com.chainsentinel")
public class MarketGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarketGatewayApplication.class, args);
	}
}
