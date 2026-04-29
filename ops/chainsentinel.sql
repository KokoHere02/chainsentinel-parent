/*
 Navicat Premium Dump SQL

 Source Server         : localhost 8.39
 Source Server Type    : MySQL
 Source Server Version : 80039 (8.0.39)
 Source Host           : localhost:3306
 Source Schema         : chainsentinel

 Target Server Type    : MySQL
 Target Server Version : 80039 (8.0.39)
 File Encoding         : 65001

 Date: 29/04/2026 14:25:20
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for address_token_holding
-- ----------------------------
DROP TABLE IF EXISTS `address_token_holding`;
CREATE TABLE `address_token_holding`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `monitor_scope_id` bigint NOT NULL COMMENT 'Related monitor_address_scope.id',
  `chain_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Blockchain name',
  `network` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Network name, e.g. mainnet/sepolia',
  `address` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Wallet address (redundant copy for query convenience)',
  `token_contract` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Token contract address; use NATIVE for native coin',
  `token_symbol` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Token symbol, e.g. ETH/USDT',
  `decimals` int NULL DEFAULT NULL COMMENT 'Token decimals',
  `balance_raw` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Current balance in minimal unit (integer string)',
  `balance_updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last successful on-chain balance refresh time',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_holding_scope_token`(`monitor_scope_id` ASC, `token_contract` ASC) USING BTREE,
  INDEX `idx_holding_scope`(`monitor_scope_id` ASC) USING BTREE,
  INDEX `idx_holding_chain_network`(`chain_name` ASC, `network` ASC) USING BTREE,
  INDEX `idx_holding_token_contract`(`token_contract` ASC) USING BTREE,
  CONSTRAINT `fk_holding_monitor_scope_id` FOREIGN KEY (`monitor_scope_id`) REFERENCES `monitor_address_scope` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Latest token holdings snapshot by monitored address' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for alert_event
-- ----------------------------
DROP TABLE IF EXISTS `alert_event`;
CREATE TABLE `alert_event`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `rule_id` bigint NOT NULL COMMENT 'Related alert_rule.id',
  `asset_event_id` bigint NULL DEFAULT NULL COMMENT 'Related asset_event.id',
  `severity` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Alert severity copied from rule',
  `send_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Dispatch status: PENDING/SENT/FAILED',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT 'Webhook retry count',
  `last_error` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Last dispatch error message',
  `sent_at` timestamp NULL DEFAULT NULL COMMENT 'Successful dispatch time',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_send_status`(`send_status` ASC) USING BTREE,
  INDEX `fk_alert_event_rule_id`(`rule_id` ASC) USING BTREE,
  INDEX `fk_alert_event_asset_event_id`(`asset_event_id` ASC) USING BTREE,
  CONSTRAINT `fk_alert_event_asset_event_id` FOREIGN KEY (`asset_event_id`) REFERENCES `asset_event` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_alert_event_rule_id` FOREIGN KEY (`rule_id`) REFERENCES `alert_rule` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1336 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Generated alert events awaiting/sent by webhook' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for alert_rule
-- ----------------------------
DROP TABLE IF EXISTS `alert_rule`;
CREATE TABLE `alert_rule`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Rule name',
  `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Rule type, e.g. ADDRESS',
  `condition_json` json NOT NULL COMMENT 'Rule condition in JSON format',
  `severity` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Alert severity, e.g. LOW/MEDIUM/HIGH',
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Alert rule definitions' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for asset_dictionary
-- ----------------------------
DROP TABLE IF EXISTS `asset_dictionary`;
CREATE TABLE `asset_dictionary`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `chain_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Blockchain name, e.g. ETH/BSC',
  `symbol` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Asset symbol, e.g. BTC/ETH/USDT',
  `contract_address` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT 'Token contract address; empty for native coin',
  `decimals` int NULL DEFAULT NULL COMMENT 'Token decimals',
  `icon_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Asset icon URL for UI display',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT 'Whether this asset is enabled: 1 yes, 0 no',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_asset_identity`(`chain_name` ASC, `symbol` ASC, `contract_address` ASC) USING BTREE,
  INDEX `idx_asset_chain_symbol`(`chain_name` ASC, `symbol` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Asset master dictionary' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for asset_event
-- ----------------------------
DROP TABLE IF EXISTS `asset_event`;
CREATE TABLE `asset_event`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `chain_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Blockchain name',
  `network` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Network name',
  `block_number` bigint NOT NULL COMMENT 'Block number of the event',
  `block_hash` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Block hash',
  `tx_hash` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Transaction hash',
  `log_index` int NOT NULL COMMENT 'Log index in tx; ETH transfer uses -1',
  `from_address` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Sender address',
  `to_address` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Receiver address',
  `token_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Asset type: ETH/ERC20',
  `token_contract` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Token contract address for ERC20',
  `symbol` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Token symbol',
  `amount` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `decimals` int NULL DEFAULT NULL COMMENT 'Token decimals',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Event status: PENDING/CONFIRMED/REORGED',
  `confirmations` int NOT NULL DEFAULT 0 COMMENT 'Current confirmation count',
  `occurred_at` timestamp NOT NULL COMMENT 'On-chain event time',
  `ingested_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Ingestion time into database',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_chain_tx_log`(`chain_name` ASC, `tx_hash` ASC, `log_index` ASC) USING BTREE,
  INDEX `idx_chain_block`(`chain_name` ASC, `block_number` ASC) USING BTREE,
  INDEX `idx_from_address`(`from_address` ASC) USING BTREE,
  INDEX `idx_to_address`(`to_address` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 114583 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'On-chain asset transfer events' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for asset_price_snapshot
-- ----------------------------
DROP TABLE IF EXISTS `asset_price_snapshot`;
CREATE TABLE `asset_price_snapshot`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `asset_id` bigint NOT NULL COMMENT 'Related asset_dictionary.id',
  `provider_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Price provider name',
  `inst_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Instrument type: SPOT/MARGIN/SWAP/FUTURES/OPTION',
  `inst_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Provider instrument ID, e.g. BTC-USDT',
  `quote_symbol` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Quote currency, e.g. USDT/USD',
  `price` decimal(38, 18) NOT NULL COMMENT 'Normalized latest price',
  `bucket_ts` datetime NOT NULL COMMENT 'Snapshot bucket time (recommended 1-minute bucket)',
  `quoted_at` datetime NULL DEFAULT NULL COMMENT 'Exchange quote timestamp',
  `fetched_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Ingestion timestamp',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_asset_price_bucket`(`asset_id` ASC, `provider_name` ASC, `inst_type` ASC, `inst_id` ASC, `bucket_ts` ASC) USING BTREE,
  INDEX `idx_price_asset_bucket`(`asset_id` ASC, `bucket_ts` ASC) USING BTREE,
  INDEX `idx_price_provider_inst_bucket`(`provider_name` ASC, `inst_id` ASC, `bucket_ts` ASC) USING BTREE,
  CONSTRAINT `fk_price_snapshot_asset_id` FOREIGN KEY (`asset_id`) REFERENCES `asset_dictionary` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 30 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Asset price snapshots for audit and backtest' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chain_config
-- ----------------------------
DROP TABLE IF EXISTS `chain_config`;
CREATE TABLE `chain_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `chain_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Blockchain name, e.g. ETH',
  `network` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Network name, e.g. mainnet/sepolia',
  `rpc_http_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'RPC endpoint HTTP URL',
  `confirm_required` int NOT NULL DEFAULT 12 COMMENT 'Required confirmations to mark confirmed',
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
  `rpc_ws_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'RPC endpoint HTTP URL',
  `balance_protocol` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'HTTP',
  `active_protocol` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'HTTP OR WS',
  `rpc_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_chain_network`(`chain_name` ASC, `network` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Chain runtime configuration' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for flyway_schema_history
-- ----------------------------
DROP TABLE IF EXISTS `flyway_schema_history`;
CREATE TABLE `flyway_schema_history`  (
  `installed_rank` int NOT NULL,
  `version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `script` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `checksum` int NULL DEFAULT NULL,
  `installed_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`) USING BTREE,
  INDEX `flyway_schema_history_s_idx`(`success` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for monitor_address
-- ----------------------------
DROP TABLE IF EXISTS `monitor_address`;
CREATE TABLE `monitor_address`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `address` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Monitored wallet/address',
  `tag` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Custom tag for the address',
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_chain_address`(`address` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Address watch list' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for monitor_address_scope
-- ----------------------------
DROP TABLE IF EXISTS `monitor_address_scope`;
CREATE TABLE `monitor_address_scope`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `monitor_address_id` bigint NOT NULL COMMENT 'Related monitor_address.id',
  `chain_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Blockchain name',
  `network` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Network name, e.g. mainnet/sepolia',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT 'Whether this scope is enabled: 1 yes, 0 no',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_scope_addr_chain_network`(`monitor_address_id` ASC, `chain_name` ASC, `network` ASC) USING BTREE,
  INDEX `idx_scope_chain_network_enabled`(`chain_name` ASC, `network` ASC, `enabled` ASC) USING BTREE,
  CONSTRAINT `fk_scope_monitor_address_id` FOREIGN KEY (`monitor_address_id`) REFERENCES `monitor_address` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Per-address chain/network monitoring scope' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for monitor_scope_token
-- ----------------------------
DROP TABLE IF EXISTS `monitor_scope_token`;
CREATE TABLE `monitor_scope_token`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `monitor_scope_id` bigint NOT NULL COMMENT 'Related monitor_address_scope.id',
  `token_contract` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Token contract address; use NATIVE for native coin',
  `symbol` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Token symbol, e.g. ETH/USDT',
  `decimals` int NULL DEFAULT NULL COMMENT 'Token decimals',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT 'Whether this token in scope is enabled: 1 yes, 0 no',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_scope_token_contract`(`monitor_scope_id` ASC, `token_contract` ASC) USING BTREE,
  INDEX `idx_scope_token_enabled`(`monitor_scope_id` ASC, `enabled` ASC) USING BTREE,
  CONSTRAINT `fk_scope_token_scope_id` FOREIGN KEY (`monitor_scope_id`) REFERENCES `monitor_address_scope` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for monitor_token
-- ----------------------------
DROP TABLE IF EXISTS `monitor_token`;
CREATE TABLE `monitor_token`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chain_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `token_contract` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `symbol` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_chain_token_contract`(`chain_name` ASC, `token_contract` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for price_provider_config
-- ----------------------------
DROP TABLE IF EXISTS `price_provider_config`;
CREATE TABLE `price_provider_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `provider_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Provider name, e.g. okx/binance',
  `base_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Provider base URL',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT 'Whether provider is enabled: 1 yes, 0 no',
  `priority` int NOT NULL DEFAULT 100 COMMENT 'Smaller value means higher priority',
  `timeout_ms` int NOT NULL DEFAULT 1500 COMMENT 'HTTP timeout in milliseconds',
  `api_key_ref` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Reference key for secret manager (optional)',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_provider_name`(`provider_name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Price provider runtime configuration' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for price_pull_target
-- ----------------------------
DROP TABLE IF EXISTS `price_pull_target`;
CREATE TABLE `price_pull_target`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `asset_id` bigint NOT NULL COMMENT 'Related asset_dictionary.id',
  `provider_config_id` bigint NOT NULL COMMENT 'Related price_provider_config.id',
  `inst_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Instrument type: SPOT/MARGIN/SWAP/FUTURES/OPTION',
  `inst_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Provider instrument ID, e.g. BTC-USDT',
  `quote_symbol` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Quote currency, e.g. USDT/USD',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT 'Whether pull target is enabled: 1 yes, 0 no',
  `poll_interval_ms` int NULL DEFAULT NULL COMMENT 'Per-target pull interval in milliseconds; NULL means use global default',
  `priority` int NOT NULL DEFAULT 100 COMMENT 'Smaller value means higher pull priority',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_pull_provider_inst`(`provider_config_id` ASC, `inst_type` ASC, `inst_id` ASC) USING BTREE,
  INDEX `idx_pull_enabled_priority`(`enabled` ASC, `priority` ASC) USING BTREE,
  INDEX `idx_pull_asset`(`asset_id` ASC) USING BTREE,
  CONSTRAINT `fk_pull_target_asset_id` FOREIGN KEY (`asset_id`) REFERENCES `asset_dictionary` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_pull_target_provider_config_id` FOREIGN KEY (`provider_config_id`) REFERENCES `price_provider_config` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Price pull target configuration' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for price_tick
-- ----------------------------
DROP TABLE IF EXISTS `price_tick`;
CREATE TABLE `price_tick`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `provider_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Price provider name, e.g. okx_ws',
  `inst_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Instrument type: SPOT/MARGIN/SWAP/FUTURES/OPTION',
  `inst_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Provider instrument ID, e.g. BTC-USDT',
  `base_symbol` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Base symbol, e.g. BTC',
  `quote_symbol` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Quote symbol, e.g. USDT',
  `price` decimal(38, 18) NOT NULL COMMENT 'Raw tick price',
  `quote_ts` bigint NOT NULL COMMENT 'Exchange quote timestamp in milliseconds',
  `ingested_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Ingestion timestamp',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_provider_inst_ts`(`provider_name` ASC, `inst_id` ASC, `quote_ts` ASC) USING BTREE,
  INDEX `idx_inst_ts`(`inst_id` ASC, `quote_ts` ASC) USING BTREE,
  INDEX `idx_base_quote_ts`(`base_symbol` ASC, `quote_symbol` ASC, `quote_ts` ASC) USING BTREE,
  INDEX `idx_ingested_at`(`ingested_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2238613 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Raw ws tick stream for replay and anomaly diagnostics' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for rule_trigger_state
-- ----------------------------
DROP TABLE IF EXISTS `rule_trigger_state`;
CREATE TABLE `rule_trigger_state`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `rule_id` bigint NOT NULL COMMENT 'Related alert_rule.id',
  `target_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Target dimension key, e.g. BTC-USDT',
  `active` bit(1) NOT NULL DEFAULT b'0' COMMENT 'Whether rule is currently active (triggered state): 1 yes, 0 no',
  `last_triggered_at` timestamp NULL DEFAULT NULL COMMENT 'Last time the rule transitioned into triggered state',
  `observed_value` decimal(38, 18) NULL DEFAULT NULL COMMENT 'Latest observed value used for state transition',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_rule_target`(`rule_id` ASC, `target_key` ASC) USING BTREE,
  INDEX `idx_rule_active`(`rule_id` ASC, `active` ASC) USING BTREE,
  CONSTRAINT `fk_rule_trigger_state_rule_id` FOREIGN KEY (`rule_id`) REFERENCES `alert_rule` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Rule trigger state for ONCE mode dedup and edge-trigger evaluation' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for scan_checkpoint
-- ----------------------------
DROP TABLE IF EXISTS `scan_checkpoint`;
CREATE TABLE `scan_checkpoint`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `chain_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Blockchain name',
  `network` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Network name',
  `last_scanned_block` bigint NOT NULL DEFAULT 0 COMMENT 'Last scanned block number',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Checkpoint update time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_scan_chain_network`(`chain_name` ASC, `network` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Scanner checkpoint by chain/network' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
