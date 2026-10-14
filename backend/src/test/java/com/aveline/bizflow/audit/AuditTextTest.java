package com.aveline.bizflow.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuditTextTest {

    @Test
    void secretsAreRedactedAndBlankValuesAreDropped() {
        assertNull(AuditText.clean("   ", 100));
        String cleaned = AuditText.clean("password=hunter2 token: abc.def", 200);
        assertFalse(cleaned.contains("hunter2"));
        assertFalse(cleaned.contains("abc.def"));
        assertEquals("SETTINGS", AuditCatalog.module("SETTINGS_UPDATED", "settings"));
        assertEquals("AUTH", AuditCatalog.module("AUTH_LOGIN", "user"));
        assertEquals("INVENTORY", AuditCatalog.module("INVENTORY", "STOCK_ADJUSTMENT"));
        assertEquals("SALES", AuditCatalog.module("SALE_CREATED", "sale"));
        assertEquals("PURCHASE", AuditCatalog.module("PURCHASE_RETURNED", "purchase"));
    }
}
