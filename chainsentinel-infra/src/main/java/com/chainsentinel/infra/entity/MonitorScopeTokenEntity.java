package com.chainsentinel.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "monitor_scope_token")
public class MonitorScopeTokenEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "monitor_scope_id", nullable = false)
	private Long monitorScopeId;

	@Column(name = "token_contract", nullable = false, length = 64)
	private String tokenContract;

	@Column(name = "symbol", length = 32)
	private String symbol;

	@Column(name = "decimals")
	private Integer decimals;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled;

	public Long getId() {
		return id;
	}

	public Long getMonitorScopeId() {
		return monitorScopeId;
	}

	public void setMonitorScopeId(Long monitorScopeId) {
		this.monitorScopeId = monitorScopeId;
	}

	public String getTokenContract() {
		return tokenContract;
	}

	public void setTokenContract(String tokenContract) {
		this.tokenContract = tokenContract;
	}

	public String getSymbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public Integer getDecimals() {
		return decimals;
	}

	public void setDecimals(Integer decimals) {
		this.decimals = decimals;
	}

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}
}

