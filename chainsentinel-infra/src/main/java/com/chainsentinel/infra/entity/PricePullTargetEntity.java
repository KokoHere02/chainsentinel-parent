package com.chainsentinel.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "price_pull_target")
public class PricePullTargetEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "provider_config_id", nullable = false)
  private Long providerConfigId;

  @Column(name = "inst_type", nullable = false, length = 16)
  private String instType;

  @Column(name = "inst_id", nullable = false, length = 64)
  private String instId;

  @Column(name = "quote_symbol", nullable = false, length = 16)
  private String quoteSymbol;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled;

  @Column(name = "poll_interval_ms")
  private Integer pollIntervalMs;

  @Column(name = "priority", nullable = false)
  private Integer priority;

  public Long getId() {
    return id;
  }

  public Long getAssetId() {
    return assetId;
  }

  public void setAssetId(Long assetId) {
    this.assetId = assetId;
  }

  public Long getProviderConfigId() {
    return providerConfigId;
  }

  public void setProviderConfigId(Long providerConfigId) {
    this.providerConfigId = providerConfigId;
  }

  public String getInstType() {
    return instType;
  }

  public void setInstType(String instType) {
    this.instType = instType;
  }

  public String getInstId() {
    return instId;
  }

  public void setInstId(String instId) {
    this.instId = instId;
  }

  public String getQuoteSymbol() {
    return quoteSymbol;
  }

  public void setQuoteSymbol(String quoteSymbol) {
    this.quoteSymbol = quoteSymbol;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Integer getPollIntervalMs() {
    return pollIntervalMs;
  }

  public void setPollIntervalMs(Integer pollIntervalMs) {
    this.pollIntervalMs = pollIntervalMs;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }
}
