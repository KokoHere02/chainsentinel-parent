DROP INDEX idx_alert_rule_enabled_type ON alert_rule;

CREATE INDEX idx_alert_rule_enabled_type_id ON alert_rule (enabled, type, id);
CREATE INDEX idx_alert_rule_type_id ON alert_rule (type, id);

CREATE INDEX idx_alert_event_send_status_sent_id ON alert_event (send_status, sent_at, id);
CREATE INDEX idx_alert_event_severity_sent_id ON alert_event (severity, sent_at, id);
CREATE INDEX idx_alert_event_rule_sent_id ON alert_event (rule_id, sent_at, id);
