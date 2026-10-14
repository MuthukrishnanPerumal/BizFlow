package com.aveline.bizflow.system;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Business tables an administrator may empty. Users, roles, permissions, and company settings are omitted
 * so a reset cannot lock the application out of itself.
 */
public final class DataResetCatalog {

    private static final List<ResetTable> TABLES = List.of(
            table("sales", "Sales", "Sales", "sales", "sale_item", "sale_tender", "sales_returns"),
            table("sale_item", "Sale items", "Sales", "sale_item", "sale_item_serial", "sales_return_item"),
            table("sale_item_serial", "Sale item serials", "Sales", "sale_item_serial"),
            table("sale_tender", "Sale payments", "Sales", "sale_tender"),
            table("sales_returns", "Sales returns", "Sales", "sales_returns", "sales_return_item"),
            table("sales_return_item", "Sales return items", "Sales", "sales_return_item", "sales_return_serial"),
            table("sales_return_serial", "Sales return serials", "Sales", "sales_return_serial"),
            table("purchases", "Purchases", "Purchases", "purchases", "purchase_item", "purchase_returns"),
            table("purchase_item", "Purchase items", "Purchases", "purchase_item", "purchase_item_serial", "purchase_return_item"),
            table("purchase_item_serial", "Purchase item serials", "Purchases", "purchase_item_serial"),
            table("purchase_returns", "Purchase returns", "Purchases", "purchase_returns", "purchase_return_item"),
            table("purchase_return_item", "Purchase return items", "Purchases", "purchase_return_item", "purchase_return_serial"),
            table("purchase_return_serial", "Purchase return serials", "Purchases", "purchase_return_serial"),
            table("document_sequence", "Document numbers", "Purchases", "document_sequence"),
            table("stock_location", "Stock locations", "Inventory", "stock_location",
                    "inventory_balance", "stock_ledger", "inventory_cost_layer", "stock_adjustment", "stock_transfer", "product_serial"),
            table("inventory_balance", "Inventory balances", "Inventory", "inventory_balance"),
            table("stock_ledger", "Stock ledger", "Inventory", "stock_ledger"),
            table("inventory_cost_layer", "Cost layers", "Inventory", "inventory_cost_layer"),
            table("stock_adjustment", "Stock adjustments", "Inventory", "stock_adjustment"),
            table("stock_transfer", "Stock transfers", "Inventory", "stock_transfer", "stock_transfer_line"),
            table("stock_transfer_line", "Stock transfer lines", "Inventory", "stock_transfer_line", "stock_transfer_serial"),
            table("stock_transfer_serial", "Stock transfer serials", "Inventory", "stock_transfer_serial"),
            table("customers", "Customers", "Parties", "customers", "customer_ledger_entry", "sales", "sales_returns", "payments"),
            table("customer_ledger_entry", "Customer ledger", "Parties", "customer_ledger_entry"),
            table("suppliers", "Suppliers", "Parties", "suppliers", "supplier_ledger_entry", "purchases", "purchase_returns", "payments"),
            table("supplier_ledger_entry", "Supplier ledger", "Parties", "supplier_ledger_entry"),
            table("categories", "Categories", "Catalog", "categories", "products"),
            table("brands", "Brands", "Catalog", "brands", "products"),
            table("units", "Units", "Catalog", "units", "products", "product_unit"),
            table("products", "Products", "Catalog", "products",
                    "product_image", "product_variant", "product_barcode", "product_unit", "product_batch", "product_serial",
                    "purchase_item", "sale_item", "inventory_balance", "stock_ledger", "inventory_cost_layer",
                    "stock_adjustment", "stock_transfer_line"),
            table("product_image", "Product images", "Catalog", "product_image"),
            table("product_variant", "Product variants", "Catalog", "product_variant"),
            table("product_barcode", "Product barcodes", "Catalog", "product_barcode"),
            table("product_unit", "Product units", "Catalog", "product_unit"),
            table("product_batch", "Product batches", "Catalog", "product_batch"),
            table("product_serial", "Product serials", "Catalog", "product_serial"),
            table("payments", "Payments", "Finance", "payments", "payment_allocations"),
            table("payment_allocations", "Payment allocations", "Finance", "payment_allocations"),
            table("expenses", "Expenses", "Finance", "expenses"),
            table("journal_entries", "Journal entries", "Finance", "journal_entries", "journal_lines"),
            table("journal_lines", "Journal lines", "Finance", "journal_lines"),
            table("gl_accounts", "Ledger accounts", "Finance", "gl_accounts", "journal_lines"),
            table("app_notification", "Notifications", "Activity", "app_notification"),
            table("audit_event", "Audit log", "Activity", "audit_event"),
            table("revoked_token", "Revoked sign-in tokens", "Activity", "revoked_token"));

    private static final Map<String, ResetTable> BY_KEY = index(TABLES);

    static {
        verify(TABLES, BY_KEY);
    }

    private DataResetCatalog() {
    }

    public static List<ResetTable> tables() {
        return TABLES;
    }

    public static Optional<ResetTable> find(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_KEY.get(key));
    }

    public static List<ResetTable> deletionOrder(Set<String> selectedKeys) {
        LinkedHashSet<String> remaining = new LinkedHashSet<>();
        for (ResetTable table : TABLES) {
            if (selectedKeys.contains(table.key())) {
                remaining.add(table.key());
            }
        }
        List<ResetTable> order = new ArrayList<>();
        while (!remaining.isEmpty()) {
            String next = null;
            for (String key : remaining) {
                boolean waitingOnChild = false;
                for (String child : BY_KEY.get(key).requires()) {
                    if (remaining.contains(child)) {
                        waitingOnChild = true;
                        break;
                    }
                }
                if (!waitingOnChild) {
                    next = key;
                    break;
                }
            }
            if (next == null) {
                throw new IllegalStateException("Reset tables cannot be ordered");
            }
            order.add(BY_KEY.get(next));
            remaining.remove(next);
        }
        return List.copyOf(order);
    }

    private static Map<String, ResetTable> index(List<ResetTable> tables) {
        Map<String, ResetTable> byKey = new LinkedHashMap<>();
        for (ResetTable table : tables) {
            byKey.put(table.key(), table);
        }
        return Map.copyOf(byKey);
    }

    private static void verify(List<ResetTable> tables, Map<String, ResetTable> byKey) {
        if (byKey.size() != tables.size()) {
            throw new IllegalStateException("Duplicate reset table key");
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (ResetTable table : tables) {
            if (!table.tableName().matches("[a-z_]+")) {
                throw new IllegalStateException("Unsafe reset table name");
            }
            for (String child : table.requires()) {
                if (!byKey.containsKey(child)) {
                    throw new IllegalStateException(table.key() + " requires unknown table " + child);
                }
            }
            walk(table.key(), byKey, visiting, visited);
        }
    }

    private static void walk(String key, Map<String, ResetTable> byKey, Set<String> visiting, Set<String> visited) {
        if (visited.contains(key)) {
            return;
        }
        if (!visiting.add(key)) {
            throw new IllegalStateException("Reset table cycle at " + key);
        }
        for (String child : byKey.get(key).requires()) {
            walk(child, byKey, visiting, visited);
        }
        visiting.remove(key);
        visited.add(key);
    }

    private static ResetTable table(String key, String label, String group, String tableName, String... requires) {
        return new ResetTable(key, label, group, tableName, List.of(requires));
    }

    public record ResetTable(String key, String label, String group, String tableName, List<String> requires) {
    }
}
