package com.chainsentinel.web.api.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

	@Test
	void shouldMaskIpv4() {
		assertEquals("127.0.*.*", LogSanitizer.maskIp("127.0.0.1"));
	}

	@Test
	void shouldMaskHexAddressLikeContent() {
		String raw = "invalid address 0x1234567890abcdef1234567890abcdef12345678";
		assertEquals("invalid address 0x***", LogSanitizer.sanitizeMessage(raw));
	}

	@Test
	void shouldTruncateLongMessage() {
		String raw = "x".repeat(300);
		String sanitized = LogSanitizer.sanitizeMessage(raw);
		assertTrue(sanitized.endsWith("..."));
		assertTrue(sanitized.length() <= 259);
	}
}
