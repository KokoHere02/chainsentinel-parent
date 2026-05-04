ALTER TABLE trade_position_snapshot
    ADD COLUMN unrealized_pnl DECIMAL(38, 18) NULL COMMENT 'Unrealized profit and loss based on avg_cost and market_price' AFTER market_value,
    ADD COLUMN unrealized_pnl_ratio DECIMAL(38, 18) NULL COMMENT 'Unrealized profit and loss ratio based on avg_cost and market_price' AFTER unrealized_pnl;
