package com.chainsentinel.web.api.support.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class FixedWindowRateLimiter {

	private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

	public boolean allow(String key, int permits, long windowMs) {
		if (key == null || key.isBlank()) {
			return true;
		}
		if (permits <= 0 || windowMs <= 0L) {
			return true;
		}
		long now = System.currentTimeMillis();
		WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now));
		synchronized (counter) {
			if (now - counter.windowStartMs >= windowMs) {
				counter.windowStartMs = now;
				counter.count = 0;
			}
			if (counter.count >= permits) {
				return false;
			}
			counter.count++;
			return true;
		}
	}

	private static class WindowCounter {
		private long windowStartMs;
		private int count;

		private WindowCounter(long windowStartMs) {
			this.windowStartMs = windowStartMs;
		}
	}
}