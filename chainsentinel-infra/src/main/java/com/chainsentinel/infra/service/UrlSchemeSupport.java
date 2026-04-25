package com.chainsentinel.infra.service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

import org.springframework.util.StringUtils;

final class UrlSchemeSupport {

	private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "ws", "wss");

	private UrlSchemeSupport() {
	}

	static String requireSupported(String raw, String fieldName) {
		if (!StringUtils.hasText(raw)) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		String value = raw.trim();
		try {
			URI uri = URI.create(value);
			String scheme = uri.getScheme();
			if (!StringUtils.hasText(scheme)) {
				throw new IllegalArgumentException(fieldName + " must include scheme: http/https/ws/wss");
			}
			String normalized = scheme.toLowerCase(Locale.ROOT);
			if (!ALLOWED_SCHEMES.contains(normalized)) {
				throw new IllegalArgumentException(fieldName + " scheme not supported: " + scheme
					+ " (allowed: http/https/ws/wss)");
			}
			return value;
		} catch (IllegalArgumentException ex) {
			if (ex.getMessage() != null && ex.getMessage().contains("allowed:")) {
				throw ex;
			}
			throw new IllegalArgumentException(fieldName + " is invalid url");
		}
	}

	static String schemeOf(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		try {
			URI uri = URI.create(raw.trim());
			String scheme = uri.getScheme();
			return scheme == null ? null : scheme.toLowerCase(Locale.ROOT);
		} catch (RuntimeException ex) {
			return null;
		}
	}
}

