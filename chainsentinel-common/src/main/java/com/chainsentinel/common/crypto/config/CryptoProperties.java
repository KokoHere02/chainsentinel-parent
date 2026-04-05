package com.chainsentinel.common.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.security.crypto")
public class CryptoProperties {

	private String keyBase64;

	public String getKeyBase64() {
		return keyBase64;
	}

	public void setKeyBase64(String keyBase64) {
		this.keyBase64 = keyBase64;
	}
}