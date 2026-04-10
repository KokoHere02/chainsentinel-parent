package com.chainsentinel.infra.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "price_tick")
public class PriceTickEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "provider_name", nullable = false, length = 32)
	private String providerName;

	@Column(name = "inst_type", nullable = false, length = 16)
	private String instType;

	@Column(name = "inst_id", nullable = false, length = 64)
	private String instId;

	@Column(name = "base_symbol", nullable = false, length = 32)
	private String baseSymbol;

	@Column(name = "quote_symbol", nullable = false, length = 32)
	private String quoteSymbol;

	@Column(name = "price", nullable = false, precision = 38, scale = 18)
	private BigDecimal price;

	@Column(name = "quote_ts", nullable = false)
	private Long quoteTs;

	@Column(name = "ingested_at", insertable = false, updatable = false)
	private Instant ingestedAt;

	public Long getId() {
		return id;
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

	public String getBaseSymbol() {
		return baseSymbol;
	}

	public void setBaseSymbol(String baseSymbol) {
		this.baseSymbol = baseSymbol;
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

	public Long getQuoteTs() {
		return quoteTs;
	}

	public void setQuoteTs(Long quoteTs) {
		this.quoteTs = quoteTs;
	}

	public Instant getIngestedAt() {
		return ingestedAt;
	}

}

