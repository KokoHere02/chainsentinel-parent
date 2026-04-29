package com.chainsentinel.web.api.support;

import java.util.regex.Pattern;

public final class LogSanitizer {

	private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})(\\.\\d{1,3}){3}$");
	private static final Pattern HEX_ADDRESS = Pattern.compile("(?i)0x[0-9a-f]{16,}");
	private static final Pattern BASE58_LIKE = Pattern.compile("\\b[1-9A-HJ-NP-Za-km-z]{24,}\\b");

	private LogSanitizer() {
	}

	public static String maskIp(String ip) {
		if (ip == null || ip.isBlank()) {
			return "-";
		}
		String value = ip.trim();
		if (IPV4.matcher(value).matches()) {
			String[] parts = value.split("\\.");
			return parts[0] + "." + parts[1] + ".*.*";
		}
		if ("0:0:0:0:0:0:0:1".equals(value) || "::1".equals(value)) {
			return "::1";
		}
		// IPv6 and unknown formats: keep only short prefix for observability.
		return value.length() <= 8 ? value : value.substring(0, 8) + "...";
	}

	public static String sanitizeMessage(String raw) {
		if (raw == null || raw.isBlank()) {
			return "-";
		}
		String value = raw.trim();
		value = HEX_ADDRESS.matcher(value).replaceAll("0x***");
		value = BASE58_LIKE.matcher(value).replaceAll("***");
		if (value.length() > 256) {
			return value.substring(0, 256) + "...";
		}
		return value;
	}
}
