CREATE TABLE IF NOT EXISTS asset_price_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_id BIGINT NOT NULL,
    provider_name VARCHAR(32) NOT NULL,
    inst_type VARCHAR(16) NOT NULL,
    inst_id VARCHAR(64) NOT NULL,
    quote_symbol VARCHAR(16) NOT NULL,
    price DECIMAL(38, 18) NOT NULL,
    bucket_ts DATETIME NOT NULL,
    quoted_at DATETIME NULL,
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_asset_provider_inst_bucket (asset_id, provider_name, inst_id, bucket_ts),
    KEY idx_snapshot_inst_bucket (inst_id, bucket_ts, id),
    KEY idx_snapshot_asset_provider_inst_bucket (asset_id, provider_name, inst_id, bucket_ts)
);

CREATE TABLE IF NOT EXISTS rule_trigger_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    target_key VARCHAR(128) NOT NULL,
    active BIT(1) NOT NULL DEFAULT b'0',
    last_triggered_at TIMESTAMP NULL,
    observed_value DECIMAL(38, 18) NULL,
    UNIQUE KEY uk_rule_target (rule_id, target_key),
    KEY idx_target_rule (target_key, rule_id)
);

CREATE TABLE IF NOT EXISTS price_pull_target (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_id BIGINT NOT NULL,
    provider_config_id BIGINT NOT NULL,
    inst_type VARCHAR(16) NOT NULL,
    inst_id VARCHAR(64) NOT NULL,
    quote_symbol VARCHAR(16) NOT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    poll_interval_ms INT NULL,
    priority INT NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_target_enabled_priority (enabled, priority, id),
    KEY idx_target_inst_enabled (inst_id, enabled)
);

CREATE INDEX idx_alert_rule_enabled_type ON alert_rule (enabled, type);
CREATE INDEX idx_alert_event_created_at ON alert_event (created_at);
CREATE INDEX idx_alert_event_created_severity ON alert_event (created_at, severity);
CREATE INDEX idx_alert_event_created_rule ON alert_event (created_at, rule_id);
