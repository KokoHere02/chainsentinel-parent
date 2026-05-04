package com.chainsentinel.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trade_account")
public class TradeAccountEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false, length = 64)
	private String name;

	@Column(name = "provider", nullable = false, length = 32)
	private String provider;

	@Column(name = "account_type", nullable = false, length = 16)
	private String accountType;

	@Column(name = "env_type", nullable = false, length = 16)
	private String envType;

	@Column(name = "api_key", length = 255)
	private String apiKey;

	@Column(name = "api_secret_cipher", length = 1024)
	private String apiSecretCipher;

	@Column(name = "passphrase_cipher", length = 1024)
	private String passphraseCipher;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled;

	@Column(name = "remark", length = 255)
	private String remark;

	@Column(name = "created_by")
	private Long createdBy;

	@Column(name = "updated_by")
	private Long updatedBy;

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getAccountType() {
		return accountType;
	}

	public void setAccountType(String accountType) {
		this.accountType = accountType;
	}

	public String getEnvType() {
		return envType;
	}

	public void setEnvType(String envType) {
		this.envType = envType;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getApiSecretCipher() {
		return apiSecretCipher;
	}

	public void setApiSecretCipher(String apiSecretCipher) {
		this.apiSecretCipher = apiSecretCipher;
	}

	public String getPassphraseCipher() {
		return passphraseCipher;
	}

	public void setPassphraseCipher(String passphraseCipher) {
		this.passphraseCipher = passphraseCipher;
	}

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(Long createdBy) {
		this.createdBy = createdBy;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(Long updatedBy) {
		this.updatedBy = updatedBy;
	}
}
