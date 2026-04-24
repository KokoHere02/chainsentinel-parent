CREATE TABLE IF NOT EXISTS address_token_holding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    monitor_address_id BIGINT NOT NULL,
    chain_name VARCHAR(32) NOT NULL,
    network VARCHAR(32) NOT NULL,
    address VARCHAR(64) NOT NULL,
    token_contract VARCHAR(64) NOT NULL,
    token_symbol VARCHAR(32) NULL,
    decimals INT NULL,
    balance_raw VARCHAR(80) NOT NULL,
    balance_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_holding_addr_network_token (monitor_address_id, network, token_contract),
    KEY idx_holding_chain_network (chain_name, network),
    KEY idx_holding_token_contract (token_contract),
    CONSTRAINT fk_holding_monitor_address_id FOREIGN KEY (monitor_address_id) REFERENCES monitor_address (id)
);

