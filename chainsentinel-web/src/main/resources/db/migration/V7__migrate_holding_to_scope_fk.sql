SET @has_holding_table := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'address_token_holding'
);

SET @has_old_column := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'address_token_holding'
      AND column_name = 'monitor_address_id'
);

SET @add_new_column_sql := IF(
    @has_holding_table = 1 AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'address_token_holding'
          AND column_name = 'monitor_scope_id'
    ),
    'ALTER TABLE address_token_holding ADD COLUMN monitor_scope_id BIGINT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @add_new_column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @insert_scope_rows_sql := IF(
    @has_holding_table = 1 AND @has_old_column = 1,
    'INSERT INTO monitor_address_scope (monitor_address_id, chain_name, network, enabled)
     SELECT DISTINCT h.monitor_address_id, h.chain_name, h.network, b''1''
     FROM address_token_holding h
     LEFT JOIN monitor_address_scope s
       ON s.monitor_address_id = h.monitor_address_id
      AND s.chain_name = h.chain_name
      AND s.network = h.network
     WHERE s.id IS NULL',
    'SELECT 1'
);
PREPARE stmt FROM @insert_scope_rows_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @backfill_scope_id_sql := IF(
    @has_holding_table = 1 AND @has_old_column = 1,
    'UPDATE address_token_holding h
     JOIN monitor_address_scope s
       ON s.monitor_address_id = h.monitor_address_id
      AND s.chain_name = h.chain_name
      AND s.network = h.network
     SET h.monitor_scope_id = s.id
     WHERE h.monitor_scope_id IS NULL',
    'SELECT 1'
);
PREPARE stmt FROM @backfill_scope_id_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_old_fk_sql := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'address_token_holding'
          AND constraint_name = 'fk_holding_monitor_address_id'
    ),
    'ALTER TABLE address_token_holding DROP FOREIGN KEY fk_holding_monitor_address_id',
    'SELECT 1'
);
PREPARE stmt FROM @drop_old_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_old_uk_sql := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'address_token_holding'
          AND index_name = 'uk_holding_addr_network_token'
    ),
    'ALTER TABLE address_token_holding DROP INDEX uk_holding_addr_network_token',
    'SELECT 1'
);
PREPARE stmt FROM @drop_old_uk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_new_uk_sql := IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'address_token_holding'
          AND index_name = 'uk_holding_scope_token'
    ),
    'ALTER TABLE address_token_holding ADD UNIQUE KEY uk_holding_scope_token (monitor_scope_id, token_contract)',
    'SELECT 1'
);
PREPARE stmt FROM @add_new_uk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_scope_idx_sql := IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'address_token_holding'
          AND index_name = 'idx_holding_scope'
    ),
    'ALTER TABLE address_token_holding ADD KEY idx_holding_scope (monitor_scope_id)',
    'SELECT 1'
);
PREPARE stmt FROM @add_scope_idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_old_column_sql := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'address_token_holding'
          AND column_name = 'monitor_address_id'
    ),
    'ALTER TABLE address_token_holding DROP COLUMN monitor_address_id',
    'SELECT 1'
);
PREPARE stmt FROM @drop_old_column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @set_not_null_sql := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'address_token_holding'
          AND column_name = 'monitor_scope_id'
          AND is_nullable = 'YES'
    ),
    'ALTER TABLE address_token_holding MODIFY COLUMN monitor_scope_id BIGINT NOT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @set_not_null_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_new_fk_sql := IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'address_token_holding'
          AND constraint_name = 'fk_holding_monitor_scope_id'
    ),
    'ALTER TABLE address_token_holding ADD CONSTRAINT fk_holding_monitor_scope_id FOREIGN KEY (monitor_scope_id) REFERENCES monitor_address_scope (id)',
    'SELECT 1'
);
PREPARE stmt FROM @add_new_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
