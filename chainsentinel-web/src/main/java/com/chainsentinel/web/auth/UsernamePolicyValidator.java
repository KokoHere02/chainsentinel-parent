package com.chainsentinel.web.auth;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UsernamePolicyValidator {

	private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9_-]{1,30}[a-z0-9])?$");
	private static final Set<String> RESERVED = Set.of("admin", "root", "system");

	public String normalizeAndValidate(String username) {
		String normalized = normalize(username);
		if (!USERNAME_PATTERN.matcher(normalized).matches()) {
			throw invalid("Username must be 3-32 chars and use only lowercase letters, digits, '_' or '-'");
		}
		if (RESERVED.contains(normalized)) {
			throw invalid("Username is reserved");
		}
		return normalized;
	}

	public String normalize(String username) {
		return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
	}

	private AuthException invalid(String message) {
		return new AuthException(AuthErrorCode.AUTH_USERNAME_INVALID, HttpStatus.BAD_REQUEST, message);
	}
}
