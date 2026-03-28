CREATE TABLE IF NOT EXISTS chain_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chain_name VARCHAR(32) NOT NULL,
    network VARCHAR(32) NOT NULL,
    rpc_url VARCHAR(512) NOT NULL,
    confirm_required INT NOT NULL DEFAULT 12,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chain_network (chain_name, network)
);

CREATE TABLE IF NOT EXISTS scan_checkpoint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chain_name VARCHAR(32) NOT NULL,
    network VARCHAR(32) NOT NULL,
    last_scanned_block BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_scan_chain_network (chain_name, network)
);

CREATE TABLE IF NOT EXISTS asset_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chain_name VARCHAR(32) NOT NULL,
    network VARCHAR(32) NOT NULL,
    block_number BIGINT NOT NULL,
    block_hash VARCHAR(80) NULL,
    tx_hash VARCHAR(80) NOT NULL,
    log_index INT NOT NULL,
    from_address VARCHAR(64) NOT NULL,
    to_address VARCHAR(64) NOT NULL,
    token_type VARCHAR(16) NOT NULL,
    token_contract VARCHAR(64) NULL,
    symbol VARCHAR(32) NULL,
    amount DECIMAL(65, 0) NULL,
    decimals INT NULL,
    status VARCHAR(16) NOT NULL,
    confirmations INT NOT NULL DEFAULT 0,
    occurred_at TIMESTAMP NOT NULL,
    ingested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chain_tx_log (chain_name, tx_hash, log_index),
    KEY idx_chain_block (chain_name, block_number),
    KEY idx_from_address (from_address),
    KEY idx_to_address (to_address)
);

CREATE TABLE IF NOT EXISTS monitor_address (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chain_name VARCHAR(32) NOT NULL,
    address VARCHAR(64) NOT NULL,
    tag VARCHAR(64) NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chain_address (chain_name, address)
);

CREATE TABLE IF NOT EXISTS alert_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    condition_json JSON NOT NULL,
    severity VARCHAR(16) NOT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS alert_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    asset_event_id BIGINT NOT NULL,
    severity VARCHAR(16) NOT NULL,
    send_status VARCHAR(16) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1024) NULL,
    sent_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_send_status (send_status),
    CONSTRAINT fk_alert_event_rule_id FOREIGN KEY (rule_id) REFERENCES alert_rule (id),
    CONSTRAINT fk_alert_event_asset_event_id FOREIGN KEY (asset_event_id) REFERENCES asset_event (id)
);



