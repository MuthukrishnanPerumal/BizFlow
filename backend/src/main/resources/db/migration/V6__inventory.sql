-- Inventory locations, reservations, adjustments, transfers, and FIFO cost layers.
-- Existing balances and ledger rows stay on the default warehouse.
-- Purchase and sale transaction types remain valid.

CREATE TABLE stock_location (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    location_code   VARCHAR(32)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    is_default      BIT(1)       NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    created_by      VARCHAR(128) NOT NULL,
    updated_by      VARCHAR(128) NOT NULL,
    is_deleted      BIT(1)       NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_location_code (location_code),
    KEY idx_stock_location_status (status),
    CONSTRAINT chk_stock_location_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO stock_location (
    location_code, name, is_default, status,
    created_at, updated_at, created_by, updated_by, is_deleted, version
) VALUES
    ('MAIN', 'Main warehouse', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('STORE', 'Retail store', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0);

ALTER TABLE inventory_balance
    ADD COLUMN location_id BIGINT NULL AFTER id,
    ADD COLUMN reserved_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0 AFTER quantity_on_hand;

UPDATE inventory_balance b
    JOIN stock_location l ON l.location_code = 'MAIN'
SET b.location_id = l.id
WHERE b.location_id IS NULL;

ALTER TABLE inventory_balance
    MODIFY location_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_inventory_balance_location FOREIGN KEY (location_id) REFERENCES stock_location (id);

ALTER TABLE inventory_balance DROP INDEX uk_inventory_balance_bucket;
ALTER TABLE inventory_balance
    ADD UNIQUE KEY uk_inventory_balance_bucket (location_id, product_id, batch_code, expiry_slot),
    ADD KEY idx_inventory_balance_location (location_id, product_id);

ALTER TABLE inventory_balance DROP CHECK chk_inventory_balance_qty;
ALTER TABLE inventory_balance
    ADD CONSTRAINT chk_inventory_balance_cost CHECK (unit_cost >= 0),
    ADD CONSTRAINT chk_inventory_balance_reserved CHECK (reserved_quantity >= 0);

ALTER TABLE stock_ledger
    ADD COLUMN location_id BIGINT NULL AFTER product_id,
    ADD COLUMN reference_number VARCHAR(64) NULL AFTER reference_id;

UPDATE stock_ledger s
    JOIN stock_location l ON l.location_code = 'MAIN'
SET s.location_id = l.id
WHERE s.location_id IS NULL;

UPDATE stock_ledger
SET reference_number = reference_id
WHERE reference_number IS NULL;

ALTER TABLE stock_ledger
    MODIFY location_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_stock_ledger_location FOREIGN KEY (location_id) REFERENCES stock_location (id),
    ADD KEY idx_stock_ledger_location (location_id, product_id, entry_date),
    ADD KEY idx_stock_ledger_type (transaction_type, entry_date),
    ADD KEY idx_stock_ledger_reference_number (reference_number);

ALTER TABLE stock_ledger DROP CHECK chk_stock_ledger_type;
ALTER TABLE stock_ledger
    ADD CONSTRAINT chk_stock_ledger_type CHECK (transaction_type IN (
        'OPENING_STOCK',
        'PURCHASE',
        'PURCHASE_RETURN',
        'PURCHASE_REVERSAL',
        'SALE',
        'SALES_RETURN',
        'SALE_REVERSAL',
        'STOCK_ADJUSTMENT',
        'STOCK_TRANSFER_IN',
        'STOCK_TRANSFER_OUT'
    ));

ALTER TABLE stock_ledger DROP CHECK chk_stock_ledger_qty;
ALTER TABLE stock_ledger
    ADD CONSTRAINT chk_stock_ledger_qty CHECK (
        quantity_in >= 0
        AND quantity_out >= 0
        AND unit_cost >= 0
        AND total_value >= 0
        AND (quantity_in > 0 OR quantity_out > 0)
        AND NOT (quantity_in > 0 AND quantity_out > 0)
    );

CREATE TABLE inventory_cost_layer (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    location_id          BIGINT         NOT NULL,
    product_id           BIGINT         NOT NULL,
    batch_code           VARCHAR(64)    NOT NULL,
    expiry_date          DATE           NULL,
    received_on          DATE           NOT NULL,
    quantity_remaining   DECIMAL(19, 4) NOT NULL,
    unit_cost            DECIMAL(19, 4) NOT NULL,
    source_type          VARCHAR(64)    NOT NULL,
    source_id            VARCHAR(64)    NOT NULL,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    created_by           VARCHAR(128)   NOT NULL,
    updated_by           VARCHAR(128)   NOT NULL,
    is_deleted           BIT(1)         NOT NULL DEFAULT 0,
    version              BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_cost_layer_open (location_id, product_id, batch_code, id),
    KEY idx_cost_layer_product (product_id),
    CONSTRAINT fk_cost_layer_location FOREIGN KEY (location_id) REFERENCES stock_location (id),
    CONSTRAINT fk_cost_layer_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_cost_layer_qty CHECK (quantity_remaining >= 0 AND unit_cost >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO inventory_cost_layer (
    location_id, product_id, batch_code, expiry_date, received_on,
    quantity_remaining, unit_cost, source_type, source_id,
    created_at, updated_at, created_by, updated_by, is_deleted, version
)
SELECT
    b.location_id,
    b.product_id,
    b.batch_code,
    b.expiry_date,
    CURRENT_DATE,
    b.quantity_on_hand,
    b.unit_cost,
    'OPENING_STOCK',
    'MIGRATION',
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    'system',
    'system',
    0,
    0
FROM inventory_balance b
WHERE b.is_deleted = 0
  AND b.quantity_on_hand > 0;

CREATE TABLE stock_adjustment (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    adjustment_number   VARCHAR(32)    NOT NULL,
    location_id         BIGINT         NOT NULL,
    product_id          BIGINT         NOT NULL,
    batch_code          VARCHAR(64)    NULL,
    expiry_date         DATE           NULL,
    direction           VARCHAR(16)    NOT NULL,
    quantity            DECIMAL(19, 4) NOT NULL,
    unit_cost           DECIMAL(19, 4) NOT NULL,
    total_value         DECIMAL(19, 4) NOT NULL,
    reason              VARCHAR(32)    NOT NULL,
    reference_number    VARCHAR(64)    NULL,
    adjustment_date     DATE           NOT NULL,
    notes               VARCHAR(1000)  NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    created_by          VARCHAR(128)   NOT NULL,
    updated_by          VARCHAR(128)   NOT NULL,
    is_deleted          BIT(1)         NOT NULL DEFAULT 0,
    version             BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_adjustment_number (adjustment_number),
    KEY idx_stock_adjustment_date (adjustment_date, location_id),
    KEY idx_stock_adjustment_product (product_id, adjustment_date),
    CONSTRAINT fk_stock_adjustment_location FOREIGN KEY (location_id) REFERENCES stock_location (id),
    CONSTRAINT fk_stock_adjustment_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_stock_adjustment_direction CHECK (direction IN ('INCREASE', 'DECREASE')),
    CONSTRAINT chk_stock_adjustment_reason CHECK (reason IN (
        'OPENING_STOCK', 'DAMAGE', 'THEFT', 'COUNT_CORRECTION', 'WRITE_OFF', 'FOUND', 'OTHER'
    )),
    CONSTRAINT chk_stock_adjustment_qty CHECK (quantity > 0 AND unit_cost >= 0 AND total_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stock_transfer (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    transfer_number    VARCHAR(32)   NOT NULL,
    from_location_id   BIGINT        NOT NULL,
    to_location_id     BIGINT        NOT NULL,
    transfer_date      DATE          NOT NULL,
    reason             VARCHAR(200)  NOT NULL,
    notes              VARCHAR(1000) NULL,
    status             VARCHAR(16)   NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    created_by         VARCHAR(128)  NOT NULL,
    updated_by         VARCHAR(128)  NOT NULL,
    is_deleted         BIT(1)        NOT NULL DEFAULT 0,
    version            BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_transfer_number (transfer_number),
    KEY idx_stock_transfer_date (transfer_date, status),
    KEY idx_stock_transfer_from (from_location_id),
    KEY idx_stock_transfer_to (to_location_id),
    CONSTRAINT fk_stock_transfer_from FOREIGN KEY (from_location_id) REFERENCES stock_location (id),
    CONSTRAINT fk_stock_transfer_to FOREIGN KEY (to_location_id) REFERENCES stock_location (id),
    CONSTRAINT chk_stock_transfer_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT chk_stock_transfer_locations CHECK (from_location_id <> to_location_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stock_transfer_line (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    transfer_id    BIGINT         NOT NULL,
    line_number    INT            NOT NULL,
    product_id     BIGINT         NOT NULL,
    batch_code     VARCHAR(64)    NULL,
    expiry_date    DATE           NULL,
    quantity       DECIMAL(19, 4) NOT NULL,
    unit_cost      DECIMAL(19, 4) NOT NULL,
    total_value    DECIMAL(19, 4) NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    created_by     VARCHAR(128)   NOT NULL,
    updated_by     VARCHAR(128)   NOT NULL,
    is_deleted     BIT(1)         NOT NULL DEFAULT 0,
    version        BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_transfer_line (transfer_id, line_number),
    KEY idx_stock_transfer_line_product (product_id),
    CONSTRAINT fk_stock_transfer_line_transfer FOREIGN KEY (transfer_id) REFERENCES stock_transfer (id),
    CONSTRAINT fk_stock_transfer_line_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_stock_transfer_line_qty CHECK (quantity > 0 AND unit_cost >= 0 AND total_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stock_transfer_serial (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    transfer_line_id  BIGINT       NOT NULL,
    serial_number     VARCHAR(64)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        VARCHAR(128) NOT NULL,
    updated_by        VARCHAR(128) NOT NULL,
    is_deleted        BIT(1)       NOT NULL DEFAULT 0,
    version           BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_transfer_serial (transfer_line_id, serial_number),
    CONSTRAINT fk_stock_transfer_serial_line FOREIGN KEY (transfer_line_id) REFERENCES stock_transfer_line (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE product_serial
    ADD COLUMN location_id BIGINT NULL AFTER product_id,
    ADD KEY idx_product_serial_location (location_id),
    ADD CONSTRAINT fk_product_serial_location FOREIGN KEY (location_id) REFERENCES stock_location (id);

INSERT INTO platform_setting (
    setting_key, setting_value, description,
    created_at, updated_at, created_by, updated_by, is_deleted, version
) VALUES
    (
        'inventory.allow-negative-stock',
        'false',
        'When true, issues may take quantity below zero. Transfers still cannot exceed available stock.',
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0
    ),
    (
        'inventory.valuation-method',
        'AVERAGE',
        'Official inventory valuation. AVERAGE uses moving average cost. FIFO consumes oldest cost layers.',
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0
    );
