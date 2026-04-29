SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'chain_config'
          AND COLUMN_NAME = 'rpc_http_url'
    ),
    'SELECT 1',
    'ALTER TABLE chain_config ADD COLUMN rpc_http_url VARCHAR(512) NULL AFTER rpc_url'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'chain_config'
          AND COLUMN_NAME = 'rpc_ws_url'
    ),
    'SELECT 1',
    'ALTER TABLE chain_config ADD COLUMN rpc_ws_url VARCHAR(512) NULL AFTER rpc_http_url'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'chain_config'
          AND COLUMN_NAME = 'balance_protocol'
    ),
    'SELECT 1',
    'ALTER TABLE chain_config ADD COLUMN balance_protocol VARCHAR(8) NOT NULL DEFAULT ''HTTP'' AFTER rpc_ws_url'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE chain_config
SET rpc_http_url = rpc_url
WHERE (rpc_http_url IS NULL OR rpc_http_url = '')
  AND rpc_url IS NOT NULL
  AND rpc_url <> '';
