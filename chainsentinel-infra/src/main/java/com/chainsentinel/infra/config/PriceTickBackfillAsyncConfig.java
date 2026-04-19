package com.chainsentinel.infra.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PriceTickBackfillAsyncConfig {

	private static final int DEFAULT_GLOBAL_MAX_CONCURRENT = 1;

	@Bean("priceTickBackfillExecutor")
	public Executor priceTickBackfillExecutor(PriceTickBackfillProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("price-tick-backfill-");
		int globalMaxConcurrent = properties.getGlobalMaxConcurrent() > 0
			? properties.getGlobalMaxConcurrent()
			: DEFAULT_GLOBAL_MAX_CONCURRENT;
		executor.setCorePoolSize(globalMaxConcurrent);
		executor.setMaxPoolSize(globalMaxConcurrent);
		executor.setQueueCapacity(200);
		executor.setWaitForTasksToCompleteOnShutdown(false);
		executor.initialize();
		return executor;
	}
}