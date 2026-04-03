package com.chainsentinel.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_price_snapshot")
public class AssetPriceSnapshotEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "provider_name", nullable = false, length = 32)
  private String providerName;

  @Column(name = "inst_type", nullable = false, length = 16)
  private String instType;

  @Column(name = "inst_id", nullable = false, length = 64)
  private String instId;

  @Column(name = "quote_symbol", nullable = false, length = 16)
  private String quoteSymbol;

  @Column(name = "price", nullable = false, precision = 38, scale = 18)
  private BigDecimal price;

  @Column(name = "bucket_ts", nullable = false)
  private LocalDateTime bucketTs;

  @Column(name = "quoted_at")
  private LocalDateTime quotedAt;

  @Column(name = "fetched_at", insertable = false, updatable = false)
  private Instant fetchedAt;

  public Long getId() {
    return id;
  }

  public Long getAssetId() {
    return assetId;
  }

  public void setAssetId(Long assetId) {
    this.assetId = assetId;
  }

  public String getProviderName() {
    return providerName;
  }

  public void setProviderName(String providerName) {
    this.providerName = providerName;
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

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public LocalDateTime getBucketTs() {
    return bucketTs;
  }

  public void setBucketTs(LocalDateTime bucketTs) {
    this.bucketTs = bucketTs;
  }

  public LocalDateTime getQuotedAt() {
    return quotedAt;
  }

  public void setQuotedAt(LocalDateTime quotedAt) {
    this.quotedAt = quotedAt;
  }

  public Instant getFetchedAt() {
    return fetchedAt;
  }
}