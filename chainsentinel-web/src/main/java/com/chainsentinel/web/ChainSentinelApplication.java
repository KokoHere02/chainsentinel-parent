package com.chainsentinel.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.chainsentinel")
@ConfigurationPropertiesScan(basePackages = "com.chainsentinel")
@EnableAsync
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.chainsentinel.infra.repository")
@EntityScan(basePackages = "com.chainsentinel.infra.entity")
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class ChainSentinelApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChainSentinelApplication.class, args);
	}
}
