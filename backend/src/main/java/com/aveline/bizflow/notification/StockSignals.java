package com.aveline.bizflow.notification;

import java.math.BigDecimal;

public final class StockSignals {

    private StockSignals() {
    }

    public static NotificationKind classify(BigDecimal onHand, BigDecimal reorderLevel) {
        BigDecimal quantity = onHand == null ? BigDecimal.ZERO : onHand;
        BigDecimal reorder = reorderLevel == null ? BigDecimal.ZERO : reorderLevel;
        if (quantity.signum() <= 0) {
            return NotificationKind.OUT_OF_STOCK;
        }
        if (reorder.signum() > 0 && quantity.compareTo(reorder) <= 0) {
            return NotificationKind.LOW_STOCK;
        }
        return null;
    }
}
