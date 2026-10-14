-- Customer receipts, supplier payments, expenses, invoice allocation, and a balanced journal.

ALTER TABLE payments DROP CHECK chk_payments_method;
ALTER TABLE payments
    ADD CONSTRAINT chk_payments_method CHECK (payment_method IN (
        'CASH', 'CREDIT', 'BANK', 'BANK_TRANSFER', 'UPI', 'CHEQUE', 'CARD', 'OTHER'
    ));

ALTER TABLE payments
    ADD COLUMN allocated_amount DECIMAL(19, 4) NULL AFTER amount,
    ADD COLUMN unallocated_amount DECIMAL(19, 4) NULL AFTER allocated_amount;

UPDATE payments
SET allocated_amount = amount,
    unallocated_amount = 0
WHERE allocated_amount IS NULL;

ALTER TABLE payments
    MODIFY allocated_amount DECIMAL(19, 4) NOT NULL,
    MODIFY unallocated_amount DECIMAL(19, 4) NOT NULL;

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_settlement CHECK (
        allocated_amount >= 0
        AND unallocated_amount >= 0
        AND allocated_amount + unallocated_amount = amount
    );

CREATE TABLE gl_accounts (
    code         VARCHAR(16)  NOT NULL,
    name         VARCHAR(120) NOT NULL,
    account_type VARCHAR(16)  NOT NULL,
    PRIMARY KEY (code),
    CONSTRAINT chk_gl_accounts_type CHECK (account_type IN ('ASSET', 'LIABILITY', 'EXPENSE', 'INCOME'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO gl_accounts (code, name, account_type) VALUES
    ('1100', 'Cash', 'ASSET'),
    ('1110', 'Bank', 'ASSET'),
    ('1120', 'Card clearing', 'ASSET'),
    ('1130', 'UPI clearing', 'ASSET'),
    ('1190', 'Other funds', 'ASSET'),
    ('1200', 'Accounts receivable', 'ASSET'),
    ('1250', 'Supplier advances', 'ASSET'),
    ('2000', 'Accounts payable', 'LIABILITY'),
    ('2150', 'Customer advances', 'LIABILITY'),
    ('5000', 'Other expense', 'EXPENSE'),
    ('5100', 'Rent', 'EXPENSE'),
    ('5200', 'Utilities', 'EXPENSE'),
    ('5300', 'Salaries', 'EXPENSE'),
    ('5400', 'Transport', 'EXPENSE'),
    ('5500', 'Office', 'EXPENSE'),
    ('5600', 'Maintenance', 'EXPENSE'),
    ('5700', 'Marketing', 'EXPENSE'),
    ('5800', 'Professional fees', 'EXPENSE'),
    ('5900', 'Taxes', 'EXPENSE');

CREATE TABLE payment_allocations (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    payment_id        BIGINT         NOT NULL,
    document_type     VARCHAR(16)    NOT NULL,
    document_id       BIGINT         NOT NULL,
    document_number   VARCHAR(32)    NOT NULL,
    allocated_amount  DECIMAL(19, 4) NOT NULL,
    status            VARCHAR(16)    NOT NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(128)   NOT NULL,
    updated_by        VARCHAR(128)   NOT NULL,
    is_deleted        BIT(1)         NOT NULL DEFAULT 0,
    version           BIGINT         NOT NULL DEFAULT 0,
    live_allocation   VARCHAR(160)   GENERATED ALWAYS AS (
        CASE
            WHEN is_deleted = 0 AND status = 'POSTED'
                THEN CONCAT(payment_id, ':', document_type, ':', document_id)
            ELSE NULL
        END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_allocations_live (live_allocation),
    KEY idx_payment_allocations_payment (payment_id, status),
    KEY idx_payment_allocations_document (document_type, document_id),
    CONSTRAINT fk_payment_allocations_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT chk_payment_allocations_type CHECK (document_type IN ('SALE', 'PURCHASE')),
    CONSTRAINT chk_payment_allocations_status CHECK (status IN ('POSTED', 'REVERSED')),
    CONSTRAINT chk_payment_allocations_amount CHECK (allocated_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE expenses (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    expense_number    VARCHAR(32)    NOT NULL,
    expense_date      DATE           NOT NULL,
    category          VARCHAR(32)    NOT NULL,
    description       VARCHAR(500)   NOT NULL,
    amount            DECIMAL(19, 4) NOT NULL,
    payment_method    VARCHAR(16)    NOT NULL,
    reference_number  VARCHAR(64)    NULL,
    notes             VARCHAR(1000)  NULL,
    status            VARCHAR(16)    NOT NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(128)   NOT NULL,
    updated_by        VARCHAR(128)   NOT NULL,
    is_deleted        BIT(1)         NOT NULL DEFAULT 0,
    version           BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_expenses_number (expense_number),
    KEY idx_expenses_date (expense_date, status),
    KEY idx_expenses_category (category, expense_date),
    CONSTRAINT chk_expenses_category CHECK (category IN (
        'RENT', 'UTILITIES', 'SALARY', 'TRANSPORT', 'OFFICE',
        'MAINTENANCE', 'MARKETING', 'PROFESSIONAL', 'TAX', 'OTHER'
    )),
    CONSTRAINT chk_expenses_method CHECK (payment_method IN ('CASH', 'CARD', 'UPI', 'BANK_TRANSFER', 'OTHER')),
    CONSTRAINT chk_expenses_status CHECK (status IN ('POSTED', 'CANCELLED')),
    CONSTRAINT chk_expenses_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE journal_entries (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    entry_number  VARCHAR(32)   NOT NULL,
    entry_date    DATE          NOT NULL,
    source_type   VARCHAR(32)   NOT NULL,
    source_id     VARCHAR(64)   NOT NULL,
    narration     VARCHAR(500)  NOT NULL,
    status        VARCHAR(16)   NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    created_by    VARCHAR(128)  NOT NULL,
    updated_by    VARCHAR(128)  NOT NULL,
    is_deleted    BIT(1)        NOT NULL DEFAULT 0,
    version       BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_journal_entries_number (entry_number),
    KEY idx_journal_entries_source (source_type, source_id, status),
    CONSTRAINT chk_journal_entries_status CHECK (status IN ('POSTED', 'REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE journal_lines (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    journal_entry_id BIGINT         NOT NULL,
    line_number      INT            NOT NULL,
    account_code     VARCHAR(16)    NOT NULL,
    debit_amount     DECIMAL(19, 4) NOT NULL,
    credit_amount    DECIMAL(19, 4) NOT NULL,
    party_type       VARCHAR(16)    NULL,
    party_id         BIGINT         NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    created_by       VARCHAR(128)   NOT NULL,
    updated_by       VARCHAR(128)   NOT NULL,
    is_deleted       BIT(1)         NOT NULL DEFAULT 0,
    version          BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_journal_lines_entry_line (journal_entry_id, line_number),
    KEY idx_journal_lines_account (account_code),
    CONSTRAINT fk_journal_lines_entry FOREIGN KEY (journal_entry_id) REFERENCES journal_entries (id),
    CONSTRAINT fk_journal_lines_account FOREIGN KEY (account_code) REFERENCES gl_accounts (code),
    CONSTRAINT chk_journal_lines_party CHECK (party_type IS NULL OR party_type IN ('CUSTOMER', 'SUPPLIER')),
    CONSTRAINT chk_journal_lines_amount CHECK (
        debit_amount >= 0
        AND credit_amount >= 0
        AND (
            (debit_amount > 0 AND credit_amount = 0)
            OR (credit_amount > 0 AND debit_amount = 0)
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO permissions (
    code, module_code, name, description, sort_order,
    created_at, updated_at, created_by, updated_by, is_deleted, version
) VALUES
    ('PAYMENT_VIEW', 'FINANCE', 'View payments', 'View customer receipts and supplier payments.', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('PAYMENT_CREATE', 'FINANCE', 'Create payments', 'Record customer receipts and supplier payments.', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('PAYMENT_UPDATE', 'FINANCE', 'Update payments', 'Change a posted receipt or supplier payment.', 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('PAYMENT_CANCEL', 'FINANCE', 'Cancel payments', 'Cancel a posted receipt or supplier payment.', 40, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('EXPENSE_VIEW', 'FINANCE', 'View expenses', 'View operating expenses.', 50, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('EXPENSE_CREATE', 'FINANCE', 'Create expenses', 'Record operating expenses.', 60, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('EXPENSE_UPDATE', 'FINANCE', 'Update expenses', 'Change a posted expense.', 70, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('EXPENSE_CANCEL', 'FINANCE', 'Cancel expenses', 'Cancel a posted expense.', 80, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('RECEIVABLE_VIEW', 'FINANCE', 'View receivables', 'View customer balances and overdue invoices.', 90, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0),
    ('PAYABLE_VIEW', 'FINANCE', 'View payables', 'View supplier balances and overdue bills.', 100, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system', 0, 0);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'ACCOUNTANT')
  AND p.module_code = 'FINANCE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'SALES_USER'
  AND p.code IN ('PAYMENT_VIEW', 'RECEIVABLE_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.code = 'PURCHASE_USER'
  AND p.code IN ('PAYMENT_VIEW', 'PAYABLE_VIEW');
