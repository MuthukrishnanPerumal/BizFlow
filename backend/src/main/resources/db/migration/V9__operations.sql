-- Audit history, business settings, and in-app notifications.

ALTER TABLE audit_event
    ADD COLUMN module_code VARCHAR(64) NULL AFTER action,
    ADD COLUMN old_value TEXT NULL AFTER detail,
    ADD COLUMN new_value TEXT NULL AFTER old_value,
    ADD KEY idx_audit_event_module (module_code),
    ADD KEY idx_audit_event_action (action),
    ADD KEY idx_audit_event_resource (resource_type, resource_id);

CREATE TABLE app_notification (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    kind          VARCHAR(32)   NOT NULL,
    severity      VARCHAR(16)   NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    message       VARCHAR(1000) NOT NULL,
    module_code   VARCHAR(64)   NULL,
    entity_type   VARCHAR(128)  NULL,
    entity_id     VARCHAR(64)   NULL,
    read_at       DATETIME(6)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notification_created (created_at),
    KEY idx_notification_open (kind, entity_type, entity_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO platform_setting (
    setting_key, setting_value, description,
    created_at, updated_at, created_by, updated_by, is_deleted, version
) VALUES
    ('company.name', 'Aveline BizFlow', 'Legal or trading name printed on documents.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('company.address', '', 'Postal address printed on documents.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('company.phone', '', 'Company phone printed on documents.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('company.email', '', 'Company email printed on documents.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('company.tax-name', 'GSTIN', 'Label for the company tax registration.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('company.tax-number', '', 'Company tax registration number.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('document.invoice-prefix', 'SAL', 'Prefix for sales invoice numbers.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('document.purchase-prefix', 'PUR', 'Prefix for purchase document numbers.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('document.return-prefix', 'SRT', 'Prefix for sales return numbers.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('document.purchase-return-prefix', 'PRT', 'Prefix for purchase return numbers.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('finance.rounding-rule', 'NONE', 'NONE, NEAREST_UNIT, or NEAREST_TENTH.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('finance.default-payment-method', 'CASH', 'Default settlement method for new payments and expenses.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('app.date-format', 'medium', 'Display date pattern. medium keeps the locale medium style.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('app.decimal-precision', '2', 'Digits shown for money on screen, print, and formatted exports.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0);

INSERT INTO permissions (
    code, module_code, name, description, sort_order,
    created_at, updated_at, created_by, updated_by, is_deleted, version
) VALUES
    ('AUDIT_VIEW', 'AUDIT', 'View audit', 'View the audit log.', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('AUDIT_EXPORT', 'AUDIT', 'Export audit', 'Export the filtered audit log.', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('SETTINGS_VIEW', 'SETTINGS', 'View settings', 'View company and business settings.', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('SETTINGS_UPDATE', 'SETTINGS', 'Update settings', 'Change company and business settings.', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('NOTIFICATION_VIEW', 'NOTIFICATION', 'View notifications', 'View stock, transaction, and system notifications.', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN')
  AND p.code IN ('AUDIT_VIEW', 'AUDIT_EXPORT', 'SETTINGS_VIEW', 'SETTINGS_UPDATE', 'NOTIFICATION_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'MANAGER'
  AND p.code IN ('AUDIT_VIEW', 'NOTIFICATION_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'ACCOUNTANT'
  AND p.code IN ('AUDIT_VIEW', 'AUDIT_EXPORT', 'NOTIFICATION_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code IN ('PURCHASE_USER', 'SALES_USER', 'INVENTORY_USER', 'REPORT_VIEWER')
  AND p.code = 'NOTIFICATION_VIEW';
