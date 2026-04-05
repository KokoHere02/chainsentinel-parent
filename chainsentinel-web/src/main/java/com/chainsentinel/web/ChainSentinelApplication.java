package com.chainsentinel.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.chainsentinel")
@ConfigurationPropertiesScan(basePackages = "com.chainsentinel")
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.chainsentinel.infra.repository")
@EntityScan(basePackages = "com.chainsentinel.infra.entity")
public class ChainSentinelApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChainSentinelApplication.class, args);
	}
}
