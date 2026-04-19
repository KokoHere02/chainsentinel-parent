package com.chainsentinel.web.api.support.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

	@Test
	void shouldBlockAfterPermitsExhaustedInWindow() {
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();

		assertTrue(limiter.allow("k1", 2, 10_000L));
		assertTrue(limiter.allow("k1", 2, 10_000L));
		assertFalse(limiter.allow("k1", 2, 10_000L));
	}

	@Test
	void shouldUseDifferentKeysIndependently() {
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();

		assertTrue(limiter.allow("k1", 1, 10_000L));
		assertFalse(limiter.allow("k1", 1, 10_000L));
		assertTrue(limiter.allow("k2", 1, 10_000L));
	}
}