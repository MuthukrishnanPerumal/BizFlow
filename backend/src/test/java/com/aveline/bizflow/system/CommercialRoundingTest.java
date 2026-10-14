package com.aveline.bizflow.system;

import com.aveline.bizflow.notification.NotificationKind;
import com.aveline.bizflow.notification.StockSignals;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommercialRoundingTest {

    @Test
    void roundingRulesAndStockSignalsFollowConfiguration() {
        assertEquals(new BigDecimal("10.13"), CommercialRounding.apply(new BigDecimal("10.125"), 2, CommercialRounding.Rule.NONE));
        assertEquals(new BigDecimal("10.00"), CommercialRounding.apply(new BigDecimal("10.49"), 2, CommercialRounding.Rule.NEAREST_UNIT));
        assertEquals(new BigDecimal("10.50"), CommercialRounding.apply(new BigDecimal("10.46"), 2, CommercialRounding.Rule.NEAREST_TENTH));
        assertEquals(NotificationKind.OUT_OF_STOCK, StockSignals.classify(BigDecimal.ZERO, new BigDecimal("5")));
        assertEquals(NotificationKind.LOW_STOCK, StockSignals.classify(new BigDecimal("2"), new BigDecimal("5")));
        assertNull(StockSignals.classify(new BigDecimal("8"), new BigDecimal("5")));
        assertNull(StockSignals.classify(new BigDecimal("1"), BigDecimal.ZERO));
    }
}
