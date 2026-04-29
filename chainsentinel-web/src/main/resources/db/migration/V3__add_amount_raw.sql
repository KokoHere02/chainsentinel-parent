ALTER TABLE asset_event
    ADD COLUMN IF NOT EXISTS amount_raw VARCHAR(80) NULL AFTER amount;

