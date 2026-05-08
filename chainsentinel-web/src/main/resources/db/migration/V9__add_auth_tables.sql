CREATE TABLE IF NOT EXISTS auth_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System user account table';

CREATE TABLE IF NOT EXISTS auth_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(32) NOT NULL,
    role_name VARCHAR(64) NOT NULL DEFAULT '',
    enabled BIT(1) NOT NULL DEFAULT b'1',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System role table';

CREATE TABLE IF NOT EXISTS auth_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_user_role_user_role (user_id, role_id),
    KEY idx_auth_user_role_role_id (role_id),
    CONSTRAINT fk_auth_user_role_user_id FOREIGN KEY (user_id) REFERENCES auth_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_auth_user_role_role_id FOREIGN KEY (role_id) REFERENCES auth_role (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User-role mapping table';

CREATE TABLE IF NOT EXISTS auth_refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BIT(1) NOT NULL DEFAULT b'0',
    issued_ip VARCHAR(64) DEFAULT NULL,
    issued_ua VARCHAR(255) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_refresh_token_token_id (token_id),
    KEY idx_auth_refresh_token_user_id (user_id),
    KEY idx_auth_refresh_token_expires_at (expires_at),
    CONSTRAINT fk_auth_refresh_token_user_id FOREIGN KEY (user_id) REFERENCES auth_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Refresh token storage for token rotation and revocation';

CREATE TABLE IF NOT EXISTS auth_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT DEFAULT NULL,
    username VARCHAR(64) DEFAULT NULL,
    action VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
    resource VARCHAR(128) DEFAULT NULL,
    result VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    reason VARCHAR(255) DEFAULT '',
    trace_id VARCHAR(64) DEFAULT NULL,
    request_ip VARCHAR(64) DEFAULT NULL,
    request_path VARCHAR(255) DEFAULT '',
    request_method VARCHAR(16) DEFAULT 'UNKNOWN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_auth_audit_log_user_created_at (user_id, created_at),
    KEY idx_auth_audit_log_action_created_at (action, created_at),
    KEY idx_auth_audit_log_trace_id (trace_id),
    CONSTRAINT fk_auth_audit_log_user_id FOREIGN KEY (user_id) REFERENCES auth_user (id) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Security audit log for authentication and authorization events';

INSERT INTO auth_role(role_code, role_name, enabled)
SELECT 'ADMIN', 'Administrator', b'1'
WHERE NOT EXISTS (SELECT 1 FROM auth_role WHERE role_code = 'ADMIN');

INSERT INTO auth_role(role_code, role_name, enabled)
SELECT 'OPERATOR', 'Operator', b'1'
WHERE NOT EXISTS (SELECT 1 FROM auth_role WHERE role_code = 'OPERATOR');

INSERT INTO auth_role(role_code, role_name, enabled)
SELECT 'TRADER', 'Trader', b'1'
WHERE NOT EXISTS (SELECT 1 FROM auth_role WHERE role_code = 'TRADER');
