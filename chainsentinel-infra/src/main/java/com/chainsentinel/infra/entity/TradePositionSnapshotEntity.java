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
@Table(name = "trade_position_snapshot")
public class TradePositionSnapshotEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "account_id", nullable = false)
	private Long accountId;

	@Column(name = "symbol", nullable = false, length = 64)
	private String symbol;

	@Column(name = "base_asset", nullable = false, length = 32)
	private String baseAsset;

	@Column(name = "quote_asset", nullable = false, length = 32)
	private String quoteAsset;

	@Column(name = "quantity", nullable = false, precision = 38, scale = 18)
	private BigDecimal quantity;

	@Column(name = "avg_cost", precision = 38, scale = 18)
	private BigDecimal avgCost;

	@Column(name = "market_price", precision = 38, scale = 18)
	private BigDecimal marketPrice;

	@Column(name = "market_value", precision = 38, scale = 18)
	private BigDecimal marketValue;

	@Column(name = "unrealized_pnl", precision = 38, scale = 18)
	private BigDecimal unrealizedPnl;

	@Column(name = "unrealized_pnl_ratio", precision = 38, scale = 18)
	private BigDecimal unrealizedPnlRatio;

	@Column(name = "source", nullable = false, length = 32)
	private String source;

	@Column(name = "snapshot_time", nullable = false)
	private Instant snapshotTime;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	public Long getId() {
		return id;
	}

	public Long getAccountId() {
		return accountId;
	}

	public void setAccountId(Long accountId) {
		this.accountId = accountId;
	}

	public String getSymbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public String getBaseAsset() {
		return baseAsset;
	}

	public void setBaseAsset(String baseAsset) {
		this.baseAsset = baseAsset;
	}

	public String getQuoteAsset() {
		return quoteAsset;
	}

	public void setQuoteAsset(String quoteAsset) {
		this.quoteAsset = quoteAsset;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getAvgCost() {
		return avgCost;
	}

	public void setAvgCost(BigDecimal avgCost) {
		this.avgCost = avgCost;
	}

	public BigDecimal getMarketPrice() {
		return marketPrice;
	}

	public void setMarketPrice(BigDecimal marketPrice) {
		this.marketPrice = marketPrice;
	}

	public BigDecimal getMarketValue() {
		return marketValue;
	}

	public void setMarketValue(BigDecimal marketValue) {
		this.marketValue = marketValue;
	}

	public BigDecimal getUnrealizedPnl() {
		return unrealizedPnl;
	}

	public void setUnrealizedPnl(BigDecimal unrealizedPnl) {
		this.unrealizedPnl = unrealizedPnl;
	}

	public BigDecimal getUnrealizedPnlRatio() {
		return unrealizedPnlRatio;
	}

	public void setUnrealizedPnlRatio(BigDecimal unrealizedPnlRatio) {
		this.unrealizedPnlRatio = unrealizedPnlRatio;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public Instant getSnapshotTime() {
		return snapshotTime;
	}

	public void setSnapshotTime(Instant snapshotTime) {
		this.snapshotTime = snapshotTime;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
