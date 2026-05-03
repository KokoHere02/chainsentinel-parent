CREATE INDEX idx_alert_rule_enabled_id ON alert_rule (enabled, id);

CREATE INDEX idx_alert_event_sent_at_id ON alert_event (sent_at, id);

CREATE INDEX idx_auth_user_enabled_id ON auth_user (enabled, id);
