package com.chainsentinel.infra.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "address_token_holding", indexes = {
	@Index(name = "idx_holding_chain_network", columnList = "chain_name,network"),
	@Index(name = "idx_holding_token_contract", columnList = "token_contract")
})
public class AddressTokenHoldingEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "monitor_scope_id", nullable = false)
	private Long monitorScopeId;

	@Column(name = "chain_name", nullable = false, length = 32)
	private String chain;

	@Column(name = "network", nullable = false, length = 32)
	private String network;

	@Column(name = "address", nullable = false, length = 64)
	private String address;

	@Column(name = "token_contract", nullable = false, length = 64)
	private String tokenContract;

	@Column(name = "token_symbol", length = 32)
	private String tokenSymbol;

	@Column(name = "decimals")
	private Integer decimals;

	@Column(name = "balance_raw", nullable = false, length = 80)
	private String balanceRaw;

	@Column(name = "balance_updated_at", nullable = false)
	private Instant balanceUpdatedAt;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;

	public Long getId() {
		return id;
	}

	public Long getMonitorScopeId() {
		return monitorScopeId;
	}

	public void setMonitorScopeId(Long monitorScopeId) {
		this.monitorScopeId = monitorScopeId;
	}

	public String getChain() {
		return chain;
	}

	public void setChain(String chain) {
		this.chain = chain;
	}

	public String getNetwork() {
		return network;
	}

	public void setNetwork(String network) {
		this.network = network;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getTokenContract() {
		return tokenContract;
	}

	public void setTokenContract(String tokenContract) {
		this.tokenContract = tokenContract;
	}

	public String getTokenSymbol() {
		return tokenSymbol;
	}

	public void setTokenSymbol(String tokenSymbol) {
		this.tokenSymbol = tokenSymbol;
	}

	public Integer getDecimals() {
		return decimals;
	}

	public void setDecimals(Integer decimals) {
		this.decimals = decimals;
	}

	public String getBalanceRaw() {
		return balanceRaw;
	}

	public void setBalanceRaw(String balanceRaw) {
		this.balanceRaw = balanceRaw;
	}

	public Instant getBalanceUpdatedAt() {
		return balanceUpdatedAt;
	}

	public void setBalanceUpdatedAt(Instant balanceUpdatedAt) {
		this.balanceUpdatedAt = balanceUpdatedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
