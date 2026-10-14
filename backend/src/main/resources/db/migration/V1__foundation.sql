-- Aveline BizFlow foundation.
-- Business tables added in later migrations follow these column conventions:
--   id BIGINT AUTO_INCREMENT PRIMARY KEY
--   created_at / updated_at DATETIME(6) stored in UTC
--   created_by / updated_by VARCHAR(128)
--   is_deleted BIT(1) NOT NULL DEFAULT 0
--   version BIGINT NOT NULL for optimistic locking
-- Monetary amounts use DECIMAL(19, 4). Floating point types are not used for money.

CREATE TABLE audit_event (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    event_type    VARCHAR(64)   NOT NULL,
    actor         VARCHAR(128)  NOT NULL,
    action        VARCHAR(128)  NOT NULL,
    resource_type VARCHAR(128)  NULL,
    resource_id   VARCHAR(64)   NULL,
    outcome       VARCHAR(32)   NOT NULL,
    detail        VARCHAR(1000) NULL,
    ip_address    VARCHAR(64)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_event_created_at (created_at),
    KEY idx_audit_event_actor (actor),
    KEY idx_audit_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE platform_setting (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    setting_key   VARCHAR(128)  NOT NULL,
    setting_value VARCHAR(2000) NOT NULL,
    description   VARCHAR(500)  NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    created_by    VARCHAR(128)  NOT NULL,
    updated_by    VARCHAR(128)  NOT NULL,
    is_deleted    BIT(1)        NOT NULL DEFAULT 0,
    version       BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_setting_key (setting_key),
    KEY idx_platform_setting_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO platform_setting (
    setting_key,
    setting_value,
    description,
    created_at,
    updated_at,
    created_by,
    updated_by,
    is_deleted,
    version
) VALUES
    (
        'app.display-name',
        'Aveline BizFlow',
        'Product name shown in the application shell.',
        UTC_TIMESTAMP(6),
        UTC_TIMESTAMP(6),
        'system',
        'system',
        0,
        0
    ),
    (
        'app.default-currency',
        'INR',
        'ISO 4217 currency used for monetary display.',
        UTC_TIMESTAMP(6),
        UTC_TIMESTAMP(6),
        'system',
        'system',
        0,
        0
    ),
    (
        'app.default-locale',
        'en-IN',
        'BCP 47 locale used for number and date formatting.',
        UTC_TIMESTAMP(6),
        UTC_TIMESTAMP(6),
        'system',
        'system',
        0,
        0
    );
