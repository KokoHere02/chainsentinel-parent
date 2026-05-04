ALTER TABLE trade_account_balance_snapshot
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'HTTP' COMMENT 'Snapshot source: WS/HTTP/HTTP_FALLBACK' AFTER total;

ALTER TABLE trade_position_snapshot
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'HTTP' COMMENT 'Snapshot source: WS/HTTP/HTTP_FALLBACK' AFTER unrealized_pnl_ratio;
