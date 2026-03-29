ALTER TABLE asset_event
    MODIFY COLUMN amount VARCHAR(80) NULL;

UPDATE asset_event
SET amount = amount_raw
WHERE amount IS NULL AND amount_raw IS NOT NULL;

ALTER TABLE asset_event
    DROP COLUMN amount_raw;
