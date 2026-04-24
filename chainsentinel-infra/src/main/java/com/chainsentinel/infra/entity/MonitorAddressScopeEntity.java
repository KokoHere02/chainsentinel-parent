package com.chainsentinel.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "monitor_address_scope")
public class MonitorAddressScopeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "monitor_address_id", nullable = false)
	private Long monitorAddressId;

	@Column(name = "chain_name", nullable = false, length = 32)
	private String chain;

	@Column(name = "network", nullable = false, length = 32)
	private String network;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled;

	public Long getId() {
		return id;
	}

	public Long getMonitorAddressId() {
		return monitorAddressId;
	}

	public void setMonitorAddressId(Long monitorAddressId) {
		this.monitorAddressId = monitorAddressId;
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

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}
}

