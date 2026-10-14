-- Range scans for dashboard aggregates and paged reports.

CREATE INDEX idx_sales_invoice_status ON sales (invoice_date, status);
CREATE INDEX idx_purchases_date_status ON purchases (purchase_date, status);
CREATE INDEX idx_sales_returns_return_date ON sales_returns (return_date);
CREATE INDEX idx_purchase_returns_return_date ON purchase_returns (return_date);
CREATE INDEX idx_payments_date_status ON payments (payment_date, status);
CREATE INDEX idx_stock_ledger_date_product ON stock_ledger (entry_date, product_id);
