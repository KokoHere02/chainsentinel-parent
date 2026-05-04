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
@Table(name = "trade_account_balance_snapshot")
public class TradeAccountBalanceSnapshotEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "account_id", nullable = false)
	private Long accountId;

	@Column(name = "asset", nullable = false, length = 32)
	private String asset;

	@Column(name = "available", nullable = false, precision = 38, scale = 18)
	private BigDecimal available;

	@Column(name = "frozen", nullable = false, precision = 38, scale = 18)
	private BigDecimal frozen;

	@Column(name = "total", nullable = false, precision = 38, scale = 18)
	private BigDecimal total;

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

	public String getAsset() {
		return asset;
	}

	public void setAsset(String asset) {
		this.asset = asset;
	}

	public BigDecimal getAvailable() {
		return available;
	}

	public void setAvailable(BigDecimal available) {
		this.available = available;
	}

	public BigDecimal getFrozen() {
		return frozen;
	}

	public void setFrozen(BigDecimal frozen) {
		this.frozen = frozen;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public void setTotal(BigDecimal total) {
		this.total = total;
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
