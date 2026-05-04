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
@Table(name = "trade_order")
public class TradeOrderEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "account_id", nullable = false)
	private Long accountId;

	@Column(name = "client_order_id", nullable = false, length = 64)
	private String clientOrderId;

	@Column(name = "provider", nullable = false, length = 32)
	private String provider;

	@Column(name = "market_type", nullable = false, length = 16)
	private String marketType;

	@Column(name = "symbol", nullable = false, length = 64)
	private String symbol;

	@Column(name = "side", nullable = false, length = 16)
	private String side;

	@Column(name = "order_type", nullable = false, length = 16)
	private String orderType;

	@Column(name = "price", precision = 38, scale = 18)
	private BigDecimal price;

	@Column(name = "quantity", nullable = false, precision = 38, scale = 18)
	private BigDecimal quantity;

	@Column(name = "quote_amount", precision = 38, scale = 18)
	private BigDecimal quoteAmount;

	@Column(name = "status", nullable = false, length = 32)
	private String status;

	@Column(name = "provider_order_id", length = 64)
	private String providerOrderId;

	@Column(name = "avg_fill_price", precision = 38, scale = 18)
	private BigDecimal avgFillPrice;

	@Column(name = "filled_quantity", nullable = false, precision = 38, scale = 18)
	private BigDecimal filledQuantity;

	@Column(name = "filled_amount", nullable = false, precision = 38, scale = 18)
	private BigDecimal filledAmount;

	@Column(name = "error_code", length = 64)
	private String errorCode;

	@Column(name = "error_message", length = 255)
	private String errorMessage;

	@Column(name = "created_by")
	private Long createdBy;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;

	public Long getId() {
		return id;
	}

	public Long getAccountId() {
		return accountId;
	}

	public void setAccountId(Long accountId) {
		this.accountId = accountId;
	}

	public String getClientOrderId() {
		return clientOrderId;
	}

	public void setClientOrderId(String clientOrderId) {
		this.clientOrderId = clientOrderId;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getMarketType() {
		return marketType;
	}

	public void setMarketType(String marketType) {
		this.marketType = marketType;
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

	public String getOrderType() {
		return orderType;
	}

	public void setOrderType(String orderType) {
		this.orderType = orderType;
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

	public BigDecimal getQuoteAmount() {
		return quoteAmount;
	}

	public void setQuoteAmount(BigDecimal quoteAmount) {
		this.quoteAmount = quoteAmount;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getProviderOrderId() {
		return providerOrderId;
	}

	public void setProviderOrderId(String providerOrderId) {
		this.providerOrderId = providerOrderId;
	}

	public BigDecimal getAvgFillPrice() {
		return avgFillPrice;
	}

	public void setAvgFillPrice(BigDecimal avgFillPrice) {
		this.avgFillPrice = avgFillPrice;
	}

	public BigDecimal getFilledQuantity() {
		return filledQuantity;
	}

	public void setFilledQuantity(BigDecimal filledQuantity) {
		this.filledQuantity = filledQuantity;
	}

	public BigDecimal getFilledAmount() {
		return filledAmount;
	}

	public void setFilledAmount(BigDecimal filledAmount) {
		this.filledAmount = filledAmount;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(Long createdBy) {
		this.createdBy = createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
