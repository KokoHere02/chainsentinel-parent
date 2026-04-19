package com.chainsentinel.web.api.support.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	private final FixedWindowRateLimiter limiter;

	public RateLimitInterceptor(FixedWindowRateLimiter limiter) {
		this.limiter = limiter;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}
		RateLimit config = resolveConfig(handlerMethod);
		if (config == null) {
			return true;
		}
		String routeKey = resolveRouteKey(config, handlerMethod.getMethod());
		String effectiveKey = switch (config.scope()) {
			case GLOBAL -> routeKey;
			case IP -> routeKey + "|" + resolveClientIp(request);
		};
		long windowMs = config.windowSeconds() * 1000L;
		if (!limiter.allow(effectiveKey, config.permits(), windowMs)) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, config.message());
		}
		return true;
	}

	private RateLimit resolveConfig(HandlerMethod method) {
		RateLimit onMethod = method.getMethodAnnotation(RateLimit.class);
		if (onMethod != null) {
			return onMethod;
		}
		return method.getBeanType().getAnnotation(RateLimit.class);
	}

	private String resolveRouteKey(RateLimit config, Method method) {
		if (config.name() != null && !config.name().isBlank()) {
			return config.name().trim();
		}
		return method.getDeclaringClass().getSimpleName() + "." + method.getName();
	}

	private String resolveClientIp(HttpServletRequest request) {
		String xff = request.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			String[] parts = xff.split(",");
			if (parts.length > 0 && !parts[0].isBlank()) {
				return parts[0].trim().toLowerCase(Locale.ROOT);
			}
		}
		String realIp = request.getHeader("X-Real-IP");
		if (realIp != null && !realIp.isBlank()) {
			return realIp.trim().toLowerCase(Locale.ROOT);
		}
		String remoteAddr = request.getRemoteAddr();
		if (remoteAddr == null || remoteAddr.isBlank()) {
			return "unknown";
		}
		return remoteAddr.trim().toLowerCase(Locale.ROOT);
	}
}