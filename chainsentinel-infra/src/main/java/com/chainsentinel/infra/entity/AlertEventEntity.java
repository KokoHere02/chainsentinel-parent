package com.chainsentinel.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "alert_event")
public class AlertEventEntity {

@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@Column(name = "rule_id", nullable = false)
private Long ruleId;

@Column(name = "asset_event_id")
private Long assetEventId;

@Column(name = "severity", nullable = false, length = 16)
private String severity;

@Column(name = "send_status", nullable = false, length = 16)
private String sendStatus;

@Column(name = "retry_count", nullable = false)
private Integer retryCount;

@Column(name = "last_error", length = 1024)
private String lastError;

@Column(name = "sent_at")
private Instant sentAt;

@Column(name = "created_at", nullable = false)
private Instant createdAt;

public Long getId() {
return id;
}

public Long getRuleId() {
return ruleId;
}

public void setRuleId(Long ruleId) {
this.ruleId = ruleId;
}

public Long getAssetEventId() {
return assetEventId;
}

public void setAssetEventId(Long assetEventId) {
this.assetEventId = assetEventId;
}

public String getSeverity() {
return severity;
}

public void setSeverity(String severity) {
this.severity = severity;
}

public String getSendStatus() {
return sendStatus;
}

public void setSendStatus(String sendStatus) {
this.sendStatus = sendStatus;
}

public Integer getRetryCount() {
return retryCount;
}

public void setRetryCount(Integer retryCount) {
this.retryCount = retryCount;
}

public String getLastError() {
return lastError;
}

public void setLastError(String lastError) {
this.lastError = lastError;
}

public Instant getSentAt() {
return sentAt;
}

public void setSentAt(Instant sentAt) {
this.sentAt = sentAt;
}

public Instant getCreatedAt() {
return createdAt;
}

public void setCreatedAt(Instant createdAt) {
this.createdAt = createdAt;
}
}
