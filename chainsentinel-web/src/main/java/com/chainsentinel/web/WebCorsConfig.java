package com.chainsentinel.web;

import com.chainsentinel.web.api.support.ratelimit.RateLimitInterceptor;
import com.chainsentinel.web.auth.AuthInterceptor;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

	private static final String DEFAULT_ALLOWED_ORIGIN = "http://localhost:5173";

	private final RateLimitInterceptor rateLimitInterceptor;
	private final AuthInterceptor authInterceptor;
	private final String[] allowedOrigins;

	public WebCorsConfig(
		RateLimitInterceptor rateLimitInterceptor,
		AuthInterceptor authInterceptor,
		@Value("${chainsentinel.web.allowed-origins:http://localhost:5173}") String allowedOriginsValue
	) {
		this.rateLimitInterceptor = rateLimitInterceptor;
		this.authInterceptor = authInterceptor;
		this.allowedOrigins = parseAllowedOrigins(allowedOriginsValue);
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
			.allowedOrigins(allowedOrigins)
			.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			.allowedHeaders("*")
			.allowCredentials(true)
			.maxAge(3600);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(rateLimitInterceptor);
		registry.addInterceptor(authInterceptor)
			.addPathPatterns("/api/**")
			.excludePathPatterns(
				"/api/auth/login",
				"/api/health",
				"/api/internal/**"
			);
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
