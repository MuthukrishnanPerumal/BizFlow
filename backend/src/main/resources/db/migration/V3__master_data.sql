-- Master data: categories, brands, units, products, suppliers, and customers.
-- Live uniqueness ignores soft-deleted rows so a retired code can be reused,
-- while two current rows cannot share a code or name.
-- Monetary amounts and quantities use DECIMAL. Floating point types are not used.
-- Extension tables (images, variants, extra barcodes, alternate units, batches, serials)
-- are reserved for later modules. Product barcode checks already honor them.

CREATE TABLE categories (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    code         VARCHAR(32)   NOT NULL,
    name         VARCHAR(160)  NOT NULL,
    description  VARCHAR(1000) NULL,
    status       VARCHAR(16)   NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    created_by   VARCHAR(128)  NOT NULL,
    updated_by   VARCHAR(128)  NOT NULL,
    is_deleted   BIT(1)        NOT NULL DEFAULT 0,
    version      BIGINT        NOT NULL DEFAULT 0,
    live_code    VARCHAR(32)   GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN code ELSE NULL END) STORED,
    live_name    VARCHAR(160)  GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN name ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_live_code (live_code),
    UNIQUE KEY uk_categories_live_name (live_name),
    KEY idx_categories_status (status),
    KEY idx_categories_deleted (is_deleted),
    CONSTRAINT chk_categories_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE brands (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    code         VARCHAR(32)   NOT NULL,
    name         VARCHAR(160)  NOT NULL,
    description  VARCHAR(1000) NULL,
    status       VARCHAR(16)   NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    created_by   VARCHAR(128)  NOT NULL,
    updated_by   VARCHAR(128)  NOT NULL,
    is_deleted   BIT(1)        NOT NULL DEFAULT 0,
    version      BIGINT        NOT NULL DEFAULT 0,
    live_code    VARCHAR(32)   GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN code ELSE NULL END) STORED,
    live_name    VARCHAR(160)  GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN name ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_brands_live_code (live_code),
    UNIQUE KEY uk_brands_live_name (live_name),
    KEY idx_brands_status (status),
    KEY idx_brands_deleted (is_deleted),
    CONSTRAINT chk_brands_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE units (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(80)  NOT NULL,
    short_name       VARCHAR(16)  NOT NULL,
    description      VARCHAR(500) NULL,
    decimal_allowed  BIT(1)       NOT NULL DEFAULT 0,
    status           VARCHAR(16)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       VARCHAR(128) NOT NULL,
    updated_by       VARCHAR(128) NOT NULL,
    is_deleted       BIT(1)       NOT NULL DEFAULT 0,
    version          BIGINT       NOT NULL DEFAULT 0,
    live_name        VARCHAR(80)  GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN name ELSE NULL END) STORED,
    live_short_name  VARCHAR(16)  GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN short_name ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_units_live_name (live_name),
    UNIQUE KEY uk_units_live_short_name (live_short_name),
    KEY idx_units_status (status),
    KEY idx_units_deleted (is_deleted),
    CONSTRAINT chk_units_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE products (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    product_code           VARCHAR(64)    NOT NULL,
    barcode                VARCHAR(64)    NULL,
    name                   VARCHAR(200)   NOT NULL,
    short_name             VARCHAR(80)    NULL,
    description            VARCHAR(2000)  NULL,
    category_id            BIGINT         NOT NULL,
    brand_id               BIGINT         NOT NULL,
    unit_id                BIGINT         NOT NULL,
    tax_percent            DECIMAL(7, 4)  NOT NULL DEFAULT 0,
    purchase_price         DECIMAL(19, 4) NOT NULL,
    selling_price          DECIMAL(19, 4) NOT NULL,
    mrp                    DECIMAL(19, 4) NOT NULL,
    minimum_selling_price  DECIMAL(19, 4) NOT NULL,
    reorder_level          DECIMAL(19, 4) NOT NULL DEFAULT 0,
    maximum_stock_level    DECIMAL(19, 4) NULL,
    stock_type             VARCHAR(16)    NOT NULL,
    batch_tracking         BIT(1)         NOT NULL DEFAULT 0,
    serial_tracking        BIT(1)         NOT NULL DEFAULT 0,
    expiry_tracking        BIT(1)         NOT NULL DEFAULT 0,
    status                 VARCHAR(16)    NOT NULL,
    created_at             DATETIME(6)    NOT NULL,
    updated_at             DATETIME(6)    NOT NULL,
    created_by             VARCHAR(128)   NOT NULL,
    updated_by             VARCHAR(128)   NOT NULL,
    is_deleted             BIT(1)         NOT NULL DEFAULT 0,
    version                BIGINT         NOT NULL DEFAULT 0,
    live_product_code      VARCHAR(64)    GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN product_code ELSE NULL END) STORED,
    live_barcode           VARCHAR(64)    GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 AND barcode IS NOT NULL THEN barcode ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_live_code (live_product_code),
    UNIQUE KEY uk_products_live_barcode (live_barcode),
    KEY idx_products_name (name),
    KEY idx_products_status (status),
    KEY idx_products_stock_type (stock_type),
    KEY idx_products_category (category_id),
    KEY idx_products_brand (brand_id),
    KEY idx_products_unit (unit_id),
    KEY idx_products_deleted (is_deleted),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id),
    CONSTRAINT fk_products_unit FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT chk_products_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_products_stock_type CHECK (stock_type IN ('NONE', 'BATCH', 'SERIAL', 'PICS')),
    CONSTRAINT chk_products_tax CHECK (tax_percent >= 0 AND tax_percent <= 100),
    CONSTRAINT chk_products_prices CHECK (
        purchase_price >= 0
        AND selling_price >= 0
        AND mrp >= 0
        AND minimum_selling_price >= 0
        AND reorder_level >= 0
        AND minimum_selling_price <= selling_price
        AND selling_price <= mrp
        AND minimum_selling_price <= mrp
        AND purchase_price <= mrp
        AND (maximum_stock_level IS NULL OR (maximum_stock_level >= 0 AND maximum_stock_level >= reorder_level))
    ),
    CONSTRAINT chk_products_tracking CHECK (
        (stock_type = 'NONE' AND batch_tracking = 0 AND serial_tracking = 0 AND expiry_tracking = 0)
        OR (stock_type = 'BATCH' AND batch_tracking = 1 AND serial_tracking = 0)
        OR (stock_type = 'SERIAL' AND serial_tracking = 1 AND batch_tracking = 0)
        OR (stock_type = 'PICS' AND batch_tracking = 0 AND serial_tracking = 0 AND expiry_tracking = 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_image (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    product_id    BIGINT        NOT NULL,
    storage_key   VARCHAR(255)  NOT NULL,
    file_name     VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(128)  NOT NULL,
    sort_order    INT           NOT NULL DEFAULT 0,
    is_primary    BIT(1)        NOT NULL DEFAULT 0,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    created_by    VARCHAR(128)  NOT NULL,
    updated_by    VARCHAR(128)  NOT NULL,
    is_deleted    BIT(1)        NOT NULL DEFAULT 0,
    version       BIGINT        NOT NULL DEFAULT 0,
    primary_marker VARCHAR(8)   GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 AND is_primary = 1 THEN 'PRIMARY' ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_image_primary (product_id, primary_marker),
    KEY idx_product_image_product (product_id, sort_order),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_variant (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    product_id   BIGINT        NOT NULL,
    sku          VARCHAR(64)   NOT NULL,
    name         VARCHAR(200)  NOT NULL,
    barcode      VARCHAR(64)   NULL,
    status       VARCHAR(16)   NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    created_by   VARCHAR(128)  NOT NULL,
    updated_by   VARCHAR(128)  NOT NULL,
    is_deleted   BIT(1)        NOT NULL DEFAULT 0,
    version      BIGINT        NOT NULL DEFAULT 0,
    live_sku     VARCHAR(64)   GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN sku ELSE NULL END) STORED,
    live_barcode VARCHAR(64)   GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 AND barcode IS NOT NULL THEN barcode ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_variant_live_sku (live_sku),
    UNIQUE KEY uk_product_variant_live_barcode (live_barcode),
    KEY idx_product_variant_product (product_id),
    CONSTRAINT fk_product_variant_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_product_variant_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_barcode (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    product_id   BIGINT       NOT NULL,
    barcode      VARCHAR(64)  NOT NULL,
    is_primary   BIT(1)       NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    created_by   VARCHAR(128) NOT NULL,
    updated_by   VARCHAR(128) NOT NULL,
    is_deleted   BIT(1)       NOT NULL DEFAULT 0,
    version      BIGINT       NOT NULL DEFAULT 0,
    live_barcode VARCHAR(64)  GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN barcode ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_barcode_live (live_barcode),
    KEY idx_product_barcode_product (product_id),
    CONSTRAINT fk_product_barcode_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_unit (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    product_id         BIGINT         NOT NULL,
    unit_id            BIGINT         NOT NULL,
    conversion_factor  DECIMAL(19, 6) NOT NULL,
    is_base            BIT(1)         NOT NULL DEFAULT 0,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    created_by         VARCHAR(128)   NOT NULL,
    updated_by         VARCHAR(128)   NOT NULL,
    is_deleted         BIT(1)         NOT NULL DEFAULT 0,
    version            BIGINT         NOT NULL DEFAULT 0,
    live_unit_id       BIGINT         GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN unit_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_unit_live (product_id, live_unit_id),
    KEY idx_product_unit_unit (unit_id),
    CONSTRAINT fk_product_unit_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_unit_unit FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT chk_product_unit_factor CHECK (conversion_factor > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_batch (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    product_id      BIGINT       NOT NULL,
    batch_code      VARCHAR(64)  NOT NULL,
    manufactured_on DATE         NULL,
    expires_on      DATE         NULL,
    status          VARCHAR(16)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    created_by      VARCHAR(128) NOT NULL,
    updated_by      VARCHAR(128) NOT NULL,
    is_deleted      BIT(1)       NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,
    live_batch_code VARCHAR(64)  GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN batch_code ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_batch_live (product_id, live_batch_code),
    KEY idx_product_batch_expiry (expires_on),
    CONSTRAINT fk_product_batch_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_product_batch_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_serial (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    product_id      BIGINT       NOT NULL,
    serial_number   VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    created_by      VARCHAR(128) NOT NULL,
    updated_by      VARCHAR(128) NOT NULL,
    is_deleted      BIT(1)       NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,
    live_serial     VARCHAR(64)  GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN serial_number ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_serial_live (product_id, live_serial),
    CONSTRAINT fk_product_serial_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_product_serial_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE suppliers (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    supplier_code    VARCHAR(32)    NOT NULL,
    supplier_name    VARCHAR(200)   NOT NULL,
    company_name     VARCHAR(200)   NULL,
    contact_person   VARCHAR(160)   NULL,
    phone            VARCHAR(32)    NULL,
    alternate_phone  VARCHAR(32)    NULL,
    email            VARCHAR(254)   NULL,
    tax_number       VARCHAR(32)    NULL,
    address          VARCHAR(500)   NULL,
    country          VARCHAR(80)    NULL,
    state            VARCHAR(80)    NULL,
    city             VARCHAR(80)    NULL,
    pincode          VARCHAR(16)    NULL,
    opening_balance  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    credit_limit     DECIMAL(19, 4) NOT NULL DEFAULT 0,
    payment_terms    VARCHAR(120)   NULL,
    status           VARCHAR(16)    NOT NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    created_by       VARCHAR(128)   NOT NULL,
    updated_by       VARCHAR(128)   NOT NULL,
    is_deleted       BIT(1)         NOT NULL DEFAULT 0,
    version          BIGINT         NOT NULL DEFAULT 0,
    live_code        VARCHAR(32)    GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN supplier_code ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_suppliers_live_code (live_code),
    KEY idx_suppliers_name (supplier_name),
    KEY idx_suppliers_status (status),
    KEY idx_suppliers_city (city),
    KEY idx_suppliers_deleted (is_deleted),
    CONSTRAINT chk_suppliers_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_suppliers_credit CHECK (credit_limit >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE supplier_ledger_entry (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    supplier_id     BIGINT         NOT NULL,
    entry_type      VARCHAR(32)    NOT NULL,
    reference_type  VARCHAR(64)    NULL,
    reference_id    VARCHAR(64)    NULL,
    entry_date      DATE           NOT NULL,
    debit_amount    DECIMAL(19, 4) NOT NULL DEFAULT 0,
    credit_amount   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    narration       VARCHAR(500)   NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    created_by      VARCHAR(128)   NOT NULL,
    updated_by      VARCHAR(128)   NOT NULL,
    is_deleted      BIT(1)         NOT NULL DEFAULT 0,
    version         BIGINT         NOT NULL DEFAULT 0,
    opening_marker  VARCHAR(16)    GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 AND entry_type = 'OPENING' THEN 'OPENING' ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_ledger_opening (supplier_id, opening_marker),
    KEY idx_supplier_ledger_supplier (supplier_id, entry_date),
    CONSTRAINT fk_supplier_ledger_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT chk_supplier_ledger_type CHECK (entry_type IN ('OPENING', 'PURCHASE', 'PURCHASE_RETURN', 'PAYMENT', 'ADJUSTMENT')),
    CONSTRAINT chk_supplier_ledger_amounts CHECK (
        debit_amount >= 0 AND credit_amount >= 0 AND NOT (debit_amount > 0 AND credit_amount > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE customers (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    customer_code    VARCHAR(32)    NOT NULL,
    customer_name    VARCHAR(200)   NOT NULL,
    company_name     VARCHAR(200)   NULL,
    phone            VARCHAR(32)    NULL,
    alternate_phone  VARCHAR(32)    NULL,
    email            VARCHAR(254)   NULL,
    tax_number       VARCHAR(32)    NULL,
    address          VARCHAR(500)   NULL,
    country          VARCHAR(80)    NULL,
    state            VARCHAR(80)    NULL,
    city             VARCHAR(80)    NULL,
    pincode          VARCHAR(16)    NULL,
    opening_balance  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    credit_limit     DECIMAL(19, 4) NOT NULL DEFAULT 0,
    payment_terms    VARCHAR(120)   NULL,
    status           VARCHAR(16)    NOT NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    created_by       VARCHAR(128)   NOT NULL,
    updated_by       VARCHAR(128)   NOT NULL,
    is_deleted       BIT(1)         NOT NULL DEFAULT 0,
    version          BIGINT         NOT NULL DEFAULT 0,
    live_code        VARCHAR(32)    GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN customer_code ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customers_live_code (live_code),
    KEY idx_customers_name (customer_name),
    KEY idx_customers_status (status),
    KEY idx_customers_city (city),
    KEY idx_customers_deleted (is_deleted),
    CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_customers_credit CHECK (credit_limit >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE customer_ledger_entry (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    customer_id     BIGINT         NOT NULL,
    entry_type      VARCHAR(32)    NOT NULL,
    reference_type  VARCHAR(64)    NULL,
    reference_id    VARCHAR(64)    NULL,
    entry_date      DATE           NOT NULL,
    debit_amount    DECIMAL(19, 4) NOT NULL DEFAULT 0,
    credit_amount   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    narration       VARCHAR(500)   NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    created_by      VARCHAR(128)   NOT NULL,
    updated_by      VARCHAR(128)   NOT NULL,
    is_deleted      BIT(1)         NOT NULL DEFAULT 0,
    version         BIGINT         NOT NULL DEFAULT 0,
    opening_marker  VARCHAR(16)    GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 AND entry_type = 'OPENING' THEN 'OPENING' ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_ledger_opening (customer_id, opening_marker),
    KEY idx_customer_ledger_customer (customer_id, entry_date),
    CONSTRAINT fk_customer_ledger_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT chk_customer_ledger_type CHECK (entry_type IN ('OPENING', 'SALE', 'SALE_RETURN', 'PAYMENT', 'ADJUSTMENT')),
    CONSTRAINT chk_customer_ledger_amounts CHECK (
        debit_amount >= 0 AND credit_amount >= 0 AND NOT (debit_amount > 0 AND credit_amount > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO units (
    name, short_name, description, decimal_allowed, status,
    created_at, updated_at, created_by, updated_by, is_deleted, version
) VALUES
    ('Piece', 'PCS', 'Single piece. Quantities are whole numbers.', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('Box', 'BOX', 'Box of pieces. Quantities are whole numbers.', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('Kilogram', 'KG', 'Weight in kilograms. Decimal quantities are allowed.', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('Gram', 'G', 'Weight in grams. Decimal quantities are allowed.', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('Liter', 'L', 'Volume in liters. Decimal quantities are allowed.', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('Meter', 'M', 'Length in meters. Decimal quantities are allowed.', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0);

INSERT INTO permissions (
    code, module_code, name, description, sort_order,
    created_at, updated_at, created_by, updated_by, is_deleted, version
) VALUES
    ('SUPPLIER_VIEW', 'SUPPLIER', 'View', 'View suppliers and their account history.', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('SUPPLIER_CREATE', 'SUPPLIER', 'Create', 'Create suppliers.', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('SUPPLIER_UPDATE', 'SUPPLIER', 'Update', 'Update suppliers.', 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('SUPPLIER_DELETE', 'SUPPLIER', 'Delete', 'Delete suppliers that have no business transactions.', 40, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('CUSTOMER_VIEW', 'CUSTOMER', 'View', 'View customers and their account history.', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('CUSTOMER_CREATE', 'CUSTOMER', 'Create', 'Create customers.', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('CUSTOMER_UPDATE', 'CUSTOMER', 'Update', 'Update customers.', 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('CUSTOMER_DELETE', 'CUSTOMER', 'Delete', 'Delete customers that have no business transactions.', 40, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER')
  AND p.code IN (
      'SUPPLIER_VIEW', 'SUPPLIER_CREATE', 'SUPPLIER_UPDATE', 'SUPPLIER_DELETE',
      'CUSTOMER_VIEW', 'CUSTOMER_CREATE', 'CUSTOMER_UPDATE', 'CUSTOMER_DELETE'
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'PURCHASE_USER'
  AND p.code IN ('SUPPLIER_VIEW', 'SUPPLIER_CREATE', 'SUPPLIER_UPDATE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'SALES_USER'
  AND p.code IN ('CUSTOMER_VIEW', 'CUSTOMER_CREATE', 'CUSTOMER_UPDATE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'ACCOUNTANT'
  AND p.code IN ('SUPPLIER_VIEW', 'CUSTOMER_VIEW');
