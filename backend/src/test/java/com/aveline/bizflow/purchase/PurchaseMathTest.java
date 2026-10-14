package com.aveline.bizflow.purchase;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PurchaseMathTest {

    @Test
    void pricesLinesAndDocumentWithoutTrustingCallerTotals() {
        PurchaseMath.DocumentQuote quote = PurchaseMath.quote(
                List.of(new PurchaseMath.LineInput(
                        new BigDecimal("2"),
                        new BigDecimal("1"),
                        new BigDecimal("10"),
                        new BigDecimal("1"),
                        new BigDecimal("5"))),
                new BigDecimal("0.50"),
                new BigDecimal("1.25"),
                new BigDecimal("-0.2000"),
                new BigDecimal("10"));

        assertEquals(new BigDecimal("20.0000"), quote.subtotal());
        assertEquals(new BigDecimal("1.0000"), quote.itemDiscount());
        assertEquals(new BigDecimal("1.5000"), quote.totalDiscount());
        assertEquals(new BigDecimal("0.9500"), quote.tax());
        assertEquals(new BigDecimal("20.5000"), quote.grandTotal());
        assertEquals(new BigDecimal("10.5000"), quote.balanceAmount());
        assertEquals(new BigDecimal("6.3333"), quote.lines().get(0).unitCost());
    }

    @Test
    void rejectsZeroQuantityExcessDiscountAndPaidAboveTotal() {
        assertThrows(com.aveline.bizflow.common.exception.BusinessRuleException.class, () -> PurchaseMath.quote(
                List.of(new PurchaseMath.LineInput(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)),
                null, null, null, null));
        assertThrows(com.aveline.bizflow.common.exception.BusinessRuleException.class, () -> PurchaseMath.quote(
                List.of(new PurchaseMath.LineInput(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN, new BigDecimal("11"), BigDecimal.ZERO)),
                null, null, null, null));
        assertThrows(com.aveline.bizflow.common.exception.BusinessRuleException.class, () -> PurchaseMath.quote(
                List.of(new PurchaseMath.LineInput(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO)),
                null, null, null, new BigDecimal("11")));
    }
}
