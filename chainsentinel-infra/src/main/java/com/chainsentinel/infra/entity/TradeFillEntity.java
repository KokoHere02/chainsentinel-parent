package com.chainsentinel.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trade_fill")
public class TradeFillEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	@Column(name = "provider_fill_id", nullable = false, length = 64)
	private String providerFillId;

	@Column(name = "symbol", nullable = false, length = 64)
	private String symbol;

	@Column(name = "side", nullable = false, length = 16)
	private String side;

	@Column(name = "price", nullable = false, precision = 38, scale = 18)
	private BigDecimal price;

	@Column(name = "quantity", nullable = false, precision = 38, scale = 18)
	private BigDecimal quantity;

	@Column(name = "fee", precision = 38, scale = 18)
	private BigDecimal fee;

	@Column(name = "fee_currency", length = 16)
	private String feeCurrency;

	@Column(name = "filled_at")
	private Instant filledAt;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	public Long getId() {
		return id;
	}

	public Long getOrderId() {
		return orderId;
	}

	public void setOrderId(Long orderId) {
		this.orderId = orderId;
	}

	public String getProviderFillId() {
		return providerFillId;
	}

	public void setProviderFillId(String providerFillId) {
		this.providerFillId = providerFillId;
	}

	public String getSymbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public String getSide() {
		return side;
	}

	public void setSide(String side) {
		this.side = side;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getFee() {
		return fee;
	}

	public void setFee(BigDecimal fee) {
		this.fee = fee;
	}

	public String getFeeCurrency() {
		return feeCurrency;
	}

	public void setFeeCurrency(String feeCurrency) {
		this.feeCurrency = feeCurrency;
	}

	public Instant getFilledAt() {
		return filledAt;
	}

	public void setFilledAt(Instant filledAt) {
		this.filledAt = filledAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
