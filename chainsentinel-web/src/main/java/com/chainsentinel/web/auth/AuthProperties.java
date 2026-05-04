package com.chainsentinel.web.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "chainsentinel.auth")
public class AuthProperties {

	public static final String DEFAULT_JWT_SECRET = "change-me-in-production";

	private String jwtSecret = DEFAULT_JWT_SECRET;
	private long accessTokenTtlSeconds = 7200;
	private long refreshTokenTtlSeconds = 604800;
	private int loginFailMaxAttempts = 5;
	private long loginFailWindowSeconds = 300;
	private long loginLockSeconds = 600;
	private int passwordMinLength = 10;
	private boolean bootstrapAdminEnabled;
	private String bootstrapAdminUsername;
	private String bootstrapAdminCredential;

	public String getJwtSecret() {
		return jwtSecret;
	}

	public void setJwtSecret(String jwtSecret) {
		this.jwtSecret = jwtSecret;
	}

	public long getAccessTokenTtlSeconds() {
		return accessTokenTtlSeconds;
	}

	public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
	}

	public long getRefreshTokenTtlSeconds() {
		return refreshTokenTtlSeconds;
	}

	public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
		this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
	}

	public int getLoginFailMaxAttempts() {
		return loginFailMaxAttempts;
	}

	public void setLoginFailMaxAttempts(int loginFailMaxAttempts) {
		this.loginFailMaxAttempts = loginFailMaxAttempts;
	}

	public long getLoginFailWindowSeconds() {
		return loginFailWindowSeconds;
	}

	public void setLoginFailWindowSeconds(long loginFailWindowSeconds) {
		this.loginFailWindowSeconds = loginFailWindowSeconds;
	}

	public long getLoginLockSeconds() {
		return loginLockSeconds;
	}

	public void setLoginLockSeconds(long loginLockSeconds) {
		this.loginLockSeconds = loginLockSeconds;
	}

	public int getPasswordMinLength() {
		return passwordMinLength;
	}

	public void setPasswordMinLength(int passwordMinLength) {
		this.passwordMinLength = passwordMinLength;
	}

	public boolean isBootstrapAdminEnabled() {
		return bootstrapAdminEnabled;
	}

	public void setBootstrapAdminEnabled(boolean bootstrapAdminEnabled) {
		this.bootstrapAdminEnabled = bootstrapAdminEnabled;
	}

	public String getBootstrapAdminUsername() {
		return bootstrapAdminUsername;
	}

	public void setBootstrapAdminUsername(String bootstrapAdminUsername) {
		this.bootstrapAdminUsername = bootstrapAdminUsername;
	}

	public String getBootstrapAdminPassword() {
		return bootstrapAdminCredential;
	}

	public void setBootstrapAdminPassword(String value) {
		this.bootstrapAdminCredential = value;
	}
}
