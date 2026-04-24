CREATE TABLE IF NOT EXISTS monitor_address_scope (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    monitor_address_id BIGINT NOT NULL,
    chain_name VARCHAR(32) NOT NULL,
    network VARCHAR(32) NOT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_scope_addr_chain_network (monitor_address_id, chain_name, network),
    KEY idx_scope_chain_network_enabled (chain_name, network, enabled),
    CONSTRAINT fk_scope_monitor_address_id FOREIGN KEY (monitor_address_id) REFERENCES monitor_address (id)
);

CREATE TABLE IF NOT EXISTS monitor_scope_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    monitor_scope_id BIGINT NOT NULL,
    token_contract VARCHAR(64) NOT NULL,
    symbol VARCHAR(32) NULL,
    decimals INT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_scope_token_contract (monitor_scope_id, token_contract),
    KEY idx_scope_token_enabled (monitor_scope_id, enabled),
    CONSTRAINT fk_scope_token_scope_id FOREIGN KEY (monitor_scope_id) REFERENCES monitor_address_scope (id)
);

