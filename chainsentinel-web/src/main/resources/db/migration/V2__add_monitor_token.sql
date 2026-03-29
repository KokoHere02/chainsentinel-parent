CREATE TABLE IF NOT EXISTS monitor_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chain_name VARCHAR(32) NOT NULL,
    token_contract VARCHAR(64) NOT NULL,
    symbol VARCHAR(32) NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chain_token_contract (chain_name, token_contract)
);
