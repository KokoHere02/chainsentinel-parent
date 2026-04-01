package com.chainsentinel.price.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PriceProperties.class)
public class PriceConfiguration {
}
