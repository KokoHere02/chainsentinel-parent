ALTER TABLE chain_config
    ADD COLUMN IF NOT EXISTS rpc_http_url VARCHAR(512) NULL AFTER rpc_url,
    ADD COLUMN IF NOT EXISTS rpc_ws_url VARCHAR(512) NULL AFTER rpc_http_url,
    ADD COLUMN IF NOT EXISTS balance_protocol VARCHAR(8) NOT NULL DEFAULT 'HTTP' AFTER rpc_ws_url;

UPDATE chain_config
SET rpc_http_url = rpc_url
WHERE (rpc_http_url IS NULL OR rpc_http_url = '')
  AND rpc_url IS NOT NULL
  AND rpc_url <> '';

