-- Sales invoices, tenders, and sales returns.
-- Amounts and quantities are DECIMAL. Invoice numbers are allocated from document_sequence.

ALTER TABLE stock_ledger DROP CHECK chk_stock_ledger_type;
ALTER TABLE stock_ledger
    ADD CONSTRAINT chk_stock_ledger_type CHECK (transaction_type IN (
        'PURCHASE', 'PURCHASE_RETURN', 'PURCHASE_REVERSAL', 'SALE', 'SALES_RETURN', 'SALE_REVERSAL'
    ));

ALTER TABLE payments DROP CHECK chk_payments_method;
ALTER TABLE payments
    ADD CONSTRAINT chk_payments_method CHECK (payment_method IN (
        'CASH', 'CREDIT', 'BANK', 'BANK_TRANSFER', 'UPI', 'CHEQUE', 'CARD'
    ));

ALTER TABLE purchases DROP CHECK chk_purchases_payment;
ALTER TABLE purchases
    ADD CONSTRAINT chk_purchases_payment CHECK (payment_type IN (
        'CASH', 'CREDIT', 'BANK', 'BANK_TRANSFER', 'UPI', 'CHEQUE', 'CARD'
    ));

ALTER TABLE payments ADD KEY idx_payments_customer (customer_id, payment_date);

CREATE TABLE sales (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    sale_number         VARCHAR(32)    NOT NULL,
    customer_id         BIGINT         NOT NULL,
    invoice_date        DATE           NOT NULL,
    payment_type        VARCHAR(16)    NOT NULL,
    reference_number    VARCHAR(64)    NULL,
    notes               VARCHAR(1000)  NULL,
    status              VARCHAR(24)    NOT NULL,
    subtotal            DECIMAL(19, 4) NOT NULL,
    item_discount       DECIMAL(19, 4) NOT NULL,
    order_discount      DECIMAL(19, 4) NOT NULL,
    total_discount      DECIMAL(19, 4) NOT NULL,
    tax_amount          DECIMAL(19, 4) NOT NULL,
    additional_charges  DECIMAL(19, 4) NOT NULL,
    round_off           DECIMAL(19, 4) NOT NULL,
    grand_total         DECIMAL(19, 4) NOT NULL,
    paid_amount         DECIMAL(19, 4) NOT NULL,
    balance_amount      DECIMAL(19, 4) NOT NULL,
    returned_value      DECIMAL(19, 4) NOT NULL DEFAULT 0,
    refunded_amount     DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    created_by          VARCHAR(128)   NOT NULL,
    updated_by          VARCHAR(128)   NOT NULL,
    is_deleted          BIT(1)         NOT NULL DEFAULT 0,
    version             BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_number (sale_number),
    KEY idx_sales_customer_date (customer_id, invoice_date),
    KEY idx_sales_status (status),
    KEY idx_sales_payment (payment_type),
    CONSTRAINT fk_sales_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT chk_sales_status CHECK (status IN (
        'DRAFT', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'PARTIALLY_RETURNED', 'RETURNED'
    )),
    CONSTRAINT chk_sales_payment CHECK (payment_type IN (
        'CASH', 'CARD', 'UPI', 'BANK_TRANSFER', 'CREDIT', 'MIXED'
    )),
    CONSTRAINT chk_sales_amounts CHECK (
        subtotal >= 0
        AND item_discount >= 0
        AND order_discount >= 0
        AND total_discount >= 0
        AND tax_amount >= 0
        AND additional_charges >= 0
        AND grand_total >= 0
        AND paid_amount >= 0
        AND balance_amount >= 0
        AND returned_value >= 0
        AND refunded_amount >= 0
        AND paid_amount <= grand_total
        AND round_off >= -1 AND round_off <= 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sale_item (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    sale_id             BIGINT         NOT NULL,
    line_number         INT            NOT NULL,
    product_id          BIGINT         NOT NULL,
    barcode             VARCHAR(64)    NULL,
    batch_code          VARCHAR(64)    NULL,
    expiry_date         DATE           NULL,
    quantity            DECIMAL(19, 4) NOT NULL,
    selling_price       DECIMAL(19, 4) NOT NULL,
    discount_amount     DECIMAL(19, 4) NOT NULL,
    tax_percent         DECIMAL(7, 4)  NOT NULL,
    tax_amount          DECIMAL(19, 4) NOT NULL,
    line_total          DECIMAL(19, 4) NOT NULL,
    unit_cost           DECIMAL(19, 4) NOT NULL,
    returned_quantity   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    created_by          VARCHAR(128)   NOT NULL,
    updated_by          VARCHAR(128)   NOT NULL,
    is_deleted          BIT(1)         NOT NULL DEFAULT 0,
    version             BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sale_item_line (sale_id, line_number),
    KEY idx_sale_item_product (product_id),
    CONSTRAINT fk_sale_item_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_sale_item_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_sale_item_qty CHECK (
        quantity > 0
        AND selling_price >= 0
        AND discount_amount >= 0
        AND tax_percent >= 0
        AND tax_percent <= 100
        AND tax_amount >= 0
        AND line_total >= 0
        AND unit_cost >= 0
        AND returned_quantity >= 0
        AND returned_quantity <= quantity
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sale_item_serial (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    sale_item_id   BIGINT       NOT NULL,
    serial_number  VARCHAR(64)  NOT NULL,
    returned       BIT(1)       NOT NULL DEFAULT 0,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    created_by     VARCHAR(128) NOT NULL,
    updated_by     VARCHAR(128) NOT NULL,
    is_deleted     BIT(1)       NOT NULL DEFAULT 0,
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sale_item_serial (sale_item_id, serial_number),
    CONSTRAINT fk_sale_item_serial_item FOREIGN KEY (sale_item_id) REFERENCES sale_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sale_tender (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    sale_id           BIGINT         NOT NULL,
    line_number       INT            NOT NULL,
    payment_method    VARCHAR(16)    NOT NULL,
    amount            DECIMAL(19, 4) NOT NULL,
    reference_number  VARCHAR(64)    NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(128)   NOT NULL,
    updated_by        VARCHAR(128)   NOT NULL,
    is_deleted        BIT(1)         NOT NULL DEFAULT 0,
    version           BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sale_tender_line (sale_id, line_number),
    CONSTRAINT fk_sale_tender_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT chk_sale_tender_method CHECK (payment_method IN ('CASH', 'CARD', 'UPI', 'BANK_TRANSFER')),
    CONSTRAINT chk_sale_tender_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sales_returns (
    id                       BIGINT         NOT NULL AUTO_INCREMENT,
    return_number            VARCHAR(32)    NOT NULL,
    sale_id                  BIGINT         NOT NULL,
    customer_id              BIGINT         NOT NULL,
    return_date              DATE           NOT NULL,
    reason                   VARCHAR(500)   NOT NULL,
    refund_method            VARCHAR(16)    NOT NULL,
    notes                    VARCHAR(1000)  NULL,
    status                   VARCHAR(16)    NOT NULL,
    merchandise_total        DECIMAL(19, 4) NOT NULL,
    item_discount_reversal   DECIMAL(19, 4) NOT NULL,
    order_discount_reversal  DECIMAL(19, 4) NOT NULL,
    tax_reversal             DECIMAL(19, 4) NOT NULL,
    grand_total              DECIMAL(19, 4) NOT NULL,
    created_at               DATETIME(6)    NOT NULL,
    updated_at               DATETIME(6)    NOT NULL,
    created_by               VARCHAR(128)   NOT NULL,
    updated_by               VARCHAR(128)   NOT NULL,
    is_deleted               BIT(1)         NOT NULL DEFAULT 0,
    version                  BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_returns_number (return_number),
    KEY idx_sales_returns_sale (sale_id),
    KEY idx_sales_returns_customer (customer_id, return_date),
    CONSTRAINT fk_sales_returns_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_sales_returns_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT chk_sales_returns_status CHECK (status IN ('CONFIRMED')),
    CONSTRAINT chk_sales_returns_refund CHECK (refund_method IN ('CASH', 'CARD', 'UPI', 'BANK_TRANSFER', 'CREDIT')),
    CONSTRAINT chk_sales_returns_amounts CHECK (
        merchandise_total >= 0
        AND item_discount_reversal >= 0
        AND order_discount_reversal >= 0
        AND tax_reversal >= 0
        AND grand_total >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sales_return_item (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    sales_return_id    BIGINT         NOT NULL,
    sale_item_id       BIGINT         NOT NULL,
    line_number        INT            NOT NULL,
    quantity           DECIMAL(19, 4) NOT NULL,
    selling_price      DECIMAL(19, 4) NOT NULL,
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
    UNIQUE KEY uk_sales_return_item_line (sales_return_id, line_number),
    KEY idx_sales_return_item_source (sale_item_id),
    CONSTRAINT fk_sales_return_item_return FOREIGN KEY (sales_return_id) REFERENCES sales_returns (id),
    CONSTRAINT fk_sales_return_item_source FOREIGN KEY (sale_item_id) REFERENCES sale_item (id),
    CONSTRAINT chk_sales_return_item_qty CHECK (
        quantity > 0
        AND selling_price >= 0
        AND discount_amount >= 0
        AND tax_amount >= 0
        AND line_total >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sales_return_serial (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    sales_return_item_id  BIGINT       NOT NULL,
    serial_number         VARCHAR(64)  NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            VARCHAR(128) NOT NULL,
    updated_by            VARCHAR(128) NOT NULL,
    is_deleted            BIT(1)       NOT NULL DEFAULT 0,
    version               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_return_serial (sales_return_item_id, serial_number),
    CONSTRAINT fk_sales_return_serial_item FOREIGN KEY (sales_return_item_id) REFERENCES sales_return_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
