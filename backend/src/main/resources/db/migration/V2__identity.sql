-- Identity, roles, and permissions.
-- Passwords are stored only as BCrypt hashes.

CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    username              VARCHAR(64)  NOT NULL,
    full_name             VARCHAR(160) NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    phone                 VARCHAR(32)  NULL,
    password_hash         VARCHAR(100) NOT NULL,
    status                VARCHAR(16)  NOT NULL,
    last_login_at         DATETIME(6)  NULL,
    failed_login_count    INT          NOT NULL DEFAULT 0,
    session_version       INT          NOT NULL DEFAULT 0,
    must_change_password  BIT(1)       NOT NULL DEFAULT 0,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            VARCHAR(128) NOT NULL,
    updated_by            VARCHAR(128) NOT NULL,
    is_deleted            BIT(1)       NOT NULL DEFAULT 0,
    version               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_status (status),
    KEY idx_users_deleted (is_deleted),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE roles (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(500) NULL,
    system_role  BIT(1)       NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    created_by   VARCHAR(128) NOT NULL,
    updated_by   VARCHAR(128) NOT NULL,
    is_deleted   BIT(1)       NOT NULL DEFAULT 0,
    version      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_code (code),
    KEY idx_roles_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE permissions (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(64)  NOT NULL,
    module_code  VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(500) NULL,
    sort_order   INT          NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    created_by   VARCHAR(128) NOT NULL,
    updated_by   VARCHAR(128) NOT NULL,
    is_deleted   BIT(1)       NOT NULL DEFAULT 0,
    version      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (code),
    KEY idx_permissions_module (module_code),
    KEY idx_permissions_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_roles_role (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_permissions_permission (permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE revoked_token (
    jti         VARCHAR(64) NOT NULL,
    username    VARCHAR(64) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    revoked_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (jti),
    KEY idx_revoked_token_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO permissions (
    code, module_code, name, description, sort_order,
    created_at, updated_at, created_by, updated_by, is_deleted, version
) VALUES
    ('PRODUCT_VIEW', 'PRODUCT', 'View', 'View products and related master data.', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('PRODUCT_CREATE', 'PRODUCT', 'Create', 'Create products and related master data.', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('PRODUCT_UPDATE', 'PRODUCT', 'Update', 'Update products and related master data.', 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('PRODUCT_DELETE', 'PRODUCT', 'Delete', 'Delete products and related master data.', 40, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('PURCHASE_VIEW', 'PURCHASE', 'View', 'View purchase documents.', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('PURCHASE_CREATE', 'PURCHASE', 'Create', 'Create purchase documents.', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('PURCHASE_UPDATE', 'PURCHASE', 'Update', 'Update purchase documents.', 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('PURCHASE_CANCEL', 'PURCHASE', 'Cancel', 'Cancel purchase documents.', 40, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('SALES_VIEW', 'SALES', 'View', 'View sales documents.', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('SALES_CREATE', 'SALES', 'Create', 'Create sales documents.', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('SALES_UPDATE', 'SALES', 'Update', 'Update sales documents.', 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('SALES_CANCEL', 'SALES', 'Cancel', 'Cancel sales documents.', 40, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('INVENTORY_VIEW', 'INVENTORY', 'View', 'View on-hand stock.', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('INVENTORY_ADJUST', 'INVENTORY', 'Adjust', 'Adjust on-hand quantities.', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('INVENTORY_TRANSFER', 'INVENTORY', 'Transfer', 'Transfer stock between locations.', 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('REPORT_VIEW', 'REPORT', 'View', 'View operational and financial reports.', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('REPORT_EXPORT', 'REPORT', 'Export', 'Export reports.', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('USER_VIEW', 'USER', 'View', 'View user accounts.', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('USER_CREATE', 'USER', 'Create', 'Create user accounts.', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('USER_UPDATE', 'USER', 'Update', 'Update user accounts and reset passwords.', 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('USER_DELETE', 'USER', 'Delete', 'Delete user accounts.', 40, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('ROLE_VIEW', 'ROLE', 'View', 'View roles and the permission catalog.', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('ROLE_CREATE', 'ROLE', 'Create', 'Create roles.', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('ROLE_UPDATE', 'ROLE', 'Update', 'Update roles and their permissions.', 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('ROLE_DELETE', 'ROLE', 'Delete', 'Delete roles.', 40, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0);

INSERT INTO roles (
    code, name, description, system_role,
    created_at, updated_at, created_by, updated_by, is_deleted, version
) VALUES
    ('SUPER_ADMIN', 'Super administrator', 'Full access, including protection of the super administrator role.', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('ADMIN', 'Administrator', 'Day-to-day administration without deleting users or roles.', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('MANAGER', 'Manager', 'Purchasing, sales, inventory, and reports.', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('PURCHASE_USER', 'Purchase user', 'Create and maintain purchase documents.', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('SALES_USER', 'Sales user', 'Create and maintain sales documents.', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('INVENTORY_USER', 'Inventory user', 'View and move stock.', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('ACCOUNTANT', 'Accountant', 'Read purchasing and sales activity and work with reports.', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('REPORT_VIEWER', 'Report viewer', 'Read-only access to reports.', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'SUPER_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'ADMIN'
  AND p.code NOT IN ('USER_DELETE', 'ROLE_DELETE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'MANAGER'
  AND p.module_code IN ('PRODUCT', 'PURCHASE', 'SALES', 'INVENTORY', 'REPORT');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'PURCHASE_USER'
  AND p.code IN ('PRODUCT_VIEW', 'PURCHASE_VIEW', 'PURCHASE_CREATE', 'PURCHASE_UPDATE', 'INVENTORY_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'SALES_USER'
  AND p.code IN ('PRODUCT_VIEW', 'SALES_VIEW', 'SALES_CREATE', 'SALES_UPDATE', 'INVENTORY_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'INVENTORY_USER'
  AND p.code IN ('PRODUCT_VIEW', 'INVENTORY_VIEW', 'INVENTORY_ADJUST', 'INVENTORY_TRANSFER');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'ACCOUNTANT'
  AND p.code IN ('PURCHASE_VIEW', 'SALES_VIEW', 'REPORT_VIEW', 'REPORT_EXPORT');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'REPORT_VIEWER'
  AND p.code = 'REPORT_VIEW';

-- Bootstrap administrator. The initial password is changed on first sign-in.
-- Hash is BCrypt for the bootstrap password documented in the project README.
INSERT INTO users (
    username, full_name, email, phone, password_hash, status,
    last_login_at, failed_login_count, session_version, must_change_password,
    created_at, updated_at, created_by, updated_by, is_deleted, version
) VALUES (
    'admin',
    'System Administrator',
    'admin@aveline.local',
    NULL,
    '$2a$12$NihtZY71cpPtzrB9xYeKk.riB/f9hE9/vewc.dAM7lXybu2r/TgxS',
    'ACTIVE',
    NULL,
    0,
    0,
    1,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    'system',
    'system',
    0,
    0
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r
WHERE u.username = 'admin'
  AND r.code = 'SUPER_ADMIN';
