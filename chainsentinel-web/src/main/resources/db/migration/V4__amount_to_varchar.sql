ALTER TABLE asset_event
    MODIFY COLUMN amount VARCHAR(80) NULL;

SET @has_amount_raw = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'asset_event'
      AND COLUMN_NAME = 'amount_raw'
);

SET @sql = IF(
    @has_amount_raw > 0,
    'UPDATE asset_event SET amount = amount_raw WHERE amount IS NULL AND amount_raw IS NOT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    @has_amount_raw > 0,
    'ALTER TABLE asset_event DROP COLUMN amount_raw',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
