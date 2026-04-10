package com.chainsentinel.price.config;

import com.chainsentinel.price.stream.PriceTickBatchWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PriceTickBatchWriterConfiguration {

	@Bean
	@ConditionalOnMissingBean(PriceTickBatchWriter.class)
	public PriceTickBatchWriter priceTickBatchWriter() {
		return quote -> {
			// no-op by default; infra module can provide async batch persistence implementation.
		};
	}
}

