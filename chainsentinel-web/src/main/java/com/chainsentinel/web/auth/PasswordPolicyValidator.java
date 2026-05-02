package com.chainsentinel.web.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyValidator {

	private final AuthProperties authProperties;

	public PasswordPolicyValidator(AuthProperties authProperties) {
		this.authProperties = authProperties;
	}

	public void validate(String password) {
		if (password == null) {
			throw weak("Password is required");
		}
		if (password.length() < authProperties.getPasswordMinLength()) {
			throw weak("Password must be at least " + authProperties.getPasswordMinLength() + " characters");
		}
		if (!containsUppercase(password) || !containsLowercase(password) || !containsDigit(password)) {
			throw weak("Password must contain uppercase, lowercase, and digit characters");
		}
	}

	private boolean containsUppercase(String value) {
		return value.chars().anyMatch(Character::isUpperCase);
	}

	private boolean containsLowercase(String value) {
		return value.chars().anyMatch(Character::isLowerCase);
	}

	private boolean containsDigit(String value) {
		return value.chars().anyMatch(Character::isDigit);
	}

	private AuthException weak(String message) {
		return new AuthException(AuthErrorCode.AUTH_PASSWORD_WEAK, HttpStatus.BAD_REQUEST, message);
	}
}
