package com.aveline.bizflow.system;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataResetCatalogTest {

    @Test
    void deletesChildRowsBeforeParentRows() {
        List<String> order = DataResetCatalog.deletionOrder(Set.of("sales", "sale_item", "sale_item_serial")).stream()
                .map(DataResetCatalog.ResetTable::key)
                .toList();

        assertTrue(order.indexOf("sale_item_serial") < order.indexOf("sale_item"));
        assertTrue(order.indexOf("sale_item") < order.indexOf("sales"));
    }

    @Test
    void keepsIdentityAndSettingsOffTheList() {
        assertTrue(DataResetCatalog.find("users").isEmpty());
        assertTrue(DataResetCatalog.find("roles").isEmpty());
        assertTrue(DataResetCatalog.find("permissions").isEmpty());
        assertTrue(DataResetCatalog.find("platform_setting").isEmpty());
        assertFalse(DataResetCatalog.tables().isEmpty());
    }
}
