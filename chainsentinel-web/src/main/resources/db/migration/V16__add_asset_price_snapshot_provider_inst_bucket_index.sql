CREATE INDEX idx_snapshot_provider_inst_bucket
ON asset_price_snapshot (provider_name, inst_id, bucket_ts, id);
