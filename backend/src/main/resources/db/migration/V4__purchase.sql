-- Purchase documents, returns, stock ledger, inventory balances, and payments.
-- Amounts and quantities are DECIMAL. Document numbers are allocated from document_sequence.

CREATE TABLE document_sequence (
    document_type  VARCHAR(32) NOT NULL,
    sequence_year  INT         NOT NULL,
    last_number    BIGINT      NOT NULL,
    PRIMARY KEY (document_type, sequence_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchases (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    purchase_number     VARCHAR(32)    NOT NULL,
    supplier_id         BIGINT         NOT NULL,
    invoice_number      VARCHAR(64)    NOT NULL,
    purchase_date       DATE           NOT NULL,
    payment_type        VARCHAR(16)    NOT NULL,
    reference_number    VARCHAR(64)    NULL,
    notes               VARCHAR(1000)  NULL,
    status              VARCHAR(24)    NOT NULL,
    subtotal            DECIMAL(19, 4) NOT NULL,
    item_discount       DECIMAL(19, 4) NOT NULL,
    bill_discount       DECIMAL(19, 4) NOT NULL,
    total_discount      DECIMAL(19, 4) NOT NULL,
    tax_amount          DECIMAL(19, 4) NOT NULL,
    additional_charges  DECIMAL(19, 4) NOT NULL,
    round_off           DECIMAL(19, 4) NOT NULL,
    grand_total         DECIMAL(19, 4) NOT NULL,
    paid_amount         DECIMAL(19, 4) NOT NULL,
    balance_amount      DECIMAL(19, 4) NOT NULL,
    returned_value      DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    created_by          VARCHAR(128)   NOT NULL,
    updated_by          VARCHAR(128)   NOT NULL,
    is_deleted          BIT(1)         NOT NULL DEFAULT 0,
    version             BIGINT         NOT NULL DEFAULT 0,
    live_invoice        VARCHAR(64)    GENERATED ALWAYS AS (
        CASE WHEN is_deleted = 0 AND status <> 'CANCELLED' THEN invoice_number ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchases_number (purchase_number),
    UNIQUE KEY uk_purchases_supplier_invoice (supplier_id, live_invoice),
    KEY idx_purchases_supplier_date (supplier_id, purchase_date),
    KEY idx_purchases_status (status),
    KEY idx_purchases_invoice (invoice_number),
    CONSTRAINT fk_purchases_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT chk_purchases_status CHECK (status IN (
        'DRAFT', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'PARTIALLY_RETURNED', 'RETURNED'
    )),
    CONSTRAINT chk_purchases_payment CHECK (payment_type IN ('CASH', 'CREDIT', 'BANK', 'UPI', 'CHEQUE', 'CARD')),
    CONSTRAINT chk_purchases_amounts CHECK (
        subtotal >= 0
        AND item_discount >= 0
        AND bill_discount >= 0
        AND total_discount >= 0
        AND tax_amount >= 0
        AND additional_charges >= 0
        AND grand_total >= 0
        AND paid_amount >= 0
        AND balance_amount >= 0
        AND returned_value >= 0
        AND paid_amount <= grand_total
        AND round_off >= -1 AND round_off <= 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_item (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    purchase_id            BIGINT         NOT NULL,
    line_number            INT            NOT NULL,
    product_id             BIGINT         NOT NULL,
    barcode                VARCHAR(64)    NULL,
    batch_code             VARCHAR(64)    NULL,
    expiry_date            DATE           NULL,
    quantity               DECIMAL(19, 4) NOT NULL,
    free_quantity          DECIMAL(19, 4) NOT NULL,
    purchase_price         DECIMAL(19, 4) NOT NULL,
    discount_amount        DECIMAL(19, 4) NOT NULL,
    tax_percent            DECIMAL(7, 4)  NOT NULL,
    tax_amount             DECIMAL(19, 4) NOT NULL,
    line_total             DECIMAL(19, 4) NOT NULL,
    unit_cost              DECIMAL(19, 4) NOT NULL,
    stock_quantity         DECIMAL(19, 4) NOT NULL,
    returned_quantity      DECIMAL(19, 4) NOT NULL DEFAULT 0,
    returned_free_quantity DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at             DATETIME(6)    NOT NULL,
    updated_at             DATETIME(6)    NOT NULL,
    created_by             VARCHAR(128)   NOT NULL,
    updated_by             VARCHAR(128)   NOT NULL,
    is_deleted             BIT(1)         NOT NULL DEFAULT 0,
    version                BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_item_line (purchase_id, line_number),
    KEY idx_purchase_item_product (product_id),
    CONSTRAINT fk_purchase_item_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id),
    CONSTRAINT fk_purchase_item_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_purchase_item_qty CHECK (
        quantity > 0
        AND free_quantity >= 0
        AND purchase_price >= 0
        AND discount_amount >= 0
        AND tax_percent >= 0
        AND tax_percent <= 100
        AND tax_amount >= 0
        AND line_total >= 0
        AND unit_cost >= 0
        AND stock_quantity > 0
        AND returned_quantity >= 0
        AND returned_free_quantity >= 0
        AND returned_quantity <= quantity
        AND returned_free_quantity <= free_quantity
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_item_serial (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    purchase_item_id  BIGINT       NOT NULL,
    serial_number     VARCHAR(64)  NOT NULL,
    returned          BIT(1)       NOT NULL DEFAULT 0,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        VARCHAR(128) NOT NULL,
    updated_by        VARCHAR(128) NOT NULL,
    is_deleted        BIT(1)       NOT NULL DEFAULT 0,
    version           BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_item_serial (purchase_item_id, serial_number),
    CONSTRAINT fk_purchase_item_serial_item FOREIGN KEY (purchase_item_id) REFERENCES purchase_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_returns (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    return_number       VARCHAR(32)    NOT NULL,
    purchase_id         BIGINT         NOT NULL,
    supplier_id         BIGINT         NOT NULL,
    return_date         DATE           NOT NULL,
    notes               VARCHAR(1000)  NULL,
    status              VARCHAR(16)    NOT NULL,
    merchandise_total   DECIMAL(19, 4) NOT NULL,
    grand_total         DECIMAL(19, 4) NOT NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    created_by          VARCHAR(128)   NOT NULL,
    updated_by          VARCHAR(128)   NOT NULL,
    is_deleted          BIT(1)         NOT NULL DEFAULT 0,
    version             BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_returns_number (return_number),
    KEY idx_purchase_returns_purchase (purchase_id),
    KEY idx_purchase_returns_supplier (supplier_id, return_date),
    CONSTRAINT fk_purchase_returns_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id),
    CONSTRAINT fk_purchase_returns_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT chk_purchase_returns_status CHECK (status IN ('CONFIRMED')),
    CONSTRAINT chk_purchase_returns_amounts CHECK (merchandise_total >= 0 AND grand_total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_return_item (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    purchase_return_id BIGINT         NOT NULL,
    purchase_item_id   BIGINT         NOT NULL,
    line_number        INT            NOT NULL,
    quantity           DECIMAL(19, 4) NOT NULL,
    free_quantity      DECIMAL(19, 4) NOT NULL,
    purchase_price     DECIMAL(19, 4) NOT NULL,
    discount_amount    DECIMAL(19, 4) NOT NULL,
    tax_amount         DECIMAL(19, 4) NOT NULL,
    line_total         DECIMAL(19, 4) NOT NULL,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    created_by         VARCHAR(128)   NOT NULL,
    updated_by         VARCHAR(128)   NOT NULL,
    is_deleted         BIT(1)         NOT NULL DEFAULT 0,
    version            BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_return_item_line (purchase_return_id, line_number),
    KEY idx_purchase_return_item_source (purchase_item_id),
    CONSTRAINT fk_purchase_return_item_return FOREIGN KEY (purchase_return_id) REFERENCES purchase_returns (id),
    CONSTRAINT fk_purchase_return_item_source FOREIGN KEY (purchase_item_id) REFERENCES purchase_item (id),
    CONSTRAINT chk_purchase_return_item_qty CHECK (
        quantity >= 0
        AND free_quantity >= 0
        AND (quantity > 0 OR free_quantity > 0)
        AND purchase_price >= 0
        AND discount_amount >= 0
        AND tax_amount >= 0
        AND line_total >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_return_serial (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    purchase_return_item_id  BIGINT       NOT NULL,
    serial_number            VARCHAR(64)  NOT NULL,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    created_by               VARCHAR(128) NOT NULL,
    updated_by               VARCHAR(128) NOT NULL,
    is_deleted               BIT(1)       NOT NULL DEFAULT 0,
    version                  BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_return_serial (purchase_return_item_id, serial_number),
    CONSTRAINT fk_purchase_return_serial_item FOREIGN KEY (purchase_return_item_id) REFERENCES purchase_return_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_balance (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    product_id         BIGINT         NOT NULL,
    batch_code         VARCHAR(64)    NOT NULL,
    expiry_date        DATE           NULL,
    quantity_on_hand   DECIMAL(19, 4) NOT NULL,
    unit_cost          DECIMAL(19, 4) NOT NULL,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    created_by         VARCHAR(128)   NOT NULL,
    updated_by         VARCHAR(128)   NOT NULL,
    is_deleted         BIT(1)         NOT NULL DEFAULT 0,
    version            BIGINT         NOT NULL DEFAULT 0,
    expiry_slot        DATE           GENERATED ALWAYS AS (IFNULL(expiry_date, DATE '1000-01-01')) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_balance_bucket (product_id, batch_code, expiry_slot),
    KEY idx_inventory_balance_product (product_id),
    CONSTRAINT fk_inventory_balance_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_inventory_balance_qty CHECK (quantity_on_hand >= 0 AND unit_cost >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stock_ledger (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    entry_date         DATE           NOT NULL,
    product_id         BIGINT         NOT NULL,
    transaction_type   VARCHAR(32)    NOT NULL,
    reference_type     VARCHAR(64)    NOT NULL,
    reference_id       VARCHAR(64)    NOT NULL,
    batch_code         VARCHAR(64)    NULL,
    expiry_date        DATE           NULL,
    quantity_in        DECIMAL(19, 4) NOT NULL,
    quantity_out       DECIMAL(19, 4) NOT NULL,
    running_balance    DECIMAL(19, 4) NOT NULL,
    unit_cost          DECIMAL(19, 4) NOT NULL,
    total_value        DECIMAL(19, 4) NOT NULL,
    remarks            VARCHAR(500)   NULL,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    created_by         VARCHAR(128)   NOT NULL,
    updated_by         VARCHAR(128)   NOT NULL,
    is_deleted         BIT(1)         NOT NULL DEFAULT 0,
    version            BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_stock_ledger_product (product_id, entry_date),
    KEY idx_stock_ledger_reference (reference_type, reference_id),
    CONSTRAINT fk_stock_ledger_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_stock_ledger_type CHECK (transaction_type IN ('PURCHASE', 'PURCHASE_RETURN', 'PURCHASE_REVERSAL')),
    CONSTRAINT chk_stock_ledger_qty CHECK (
        quantity_in >= 0
        AND quantity_out >= 0
        AND running_balance >= 0
        AND unit_cost >= 0
        AND total_value >= 0
        AND NOT (quantity_in > 0 AND quantity_out > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payments (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    payment_number    VARCHAR(32)    NOT NULL,
    supplier_id       BIGINT         NULL,
    customer_id       BIGINT         NULL,
    direction         VARCHAR(8)     NOT NULL,
    payment_method    VARCHAR(16)    NOT NULL,
    payment_date      DATE           NOT NULL,
    amount            DECIMAL(19, 4) NOT NULL,
    source_type       VARCHAR(32)    NOT NULL,
    source_id         VARCHAR(64)    NOT NULL,
    reference_number  VARCHAR(64)    NULL,
    notes             VARCHAR(500)   NULL,
    status            VARCHAR(16)    NOT NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(128)   NOT NULL,
    updated_by        VARCHAR(128)   NOT NULL,
    is_deleted        BIT(1)         NOT NULL DEFAULT 0,
    version           BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_number (payment_number),
    KEY idx_payments_supplier (supplier_id, payment_date),
    KEY idx_payments_source (source_type, source_id),
    CONSTRAINT fk_payments_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_payments_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT chk_payments_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT chk_payments_method CHECK (payment_method IN ('CASH', 'CREDIT', 'BANK', 'UPI', 'CHEQUE', 'CARD')),
    CONSTRAINT chk_payments_status CHECK (status IN ('POSTED', 'CANCELLED')),
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_party CHECK (
        (supplier_id IS NOT NULL AND customer_id IS NULL)
        OR (supplier_id IS NULL AND customer_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
