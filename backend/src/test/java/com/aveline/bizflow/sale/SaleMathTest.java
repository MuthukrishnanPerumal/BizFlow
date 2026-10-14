package com.aveline.bizflow.sale;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SaleMathTest {

    @Test
    void pricesLinesAndDocumentOnTheServer() {
        SaleMath.DocumentQuote quote = SaleMath.quote(
                List.of(new SaleMath.LineInput(
                        new BigDecimal("2"),
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
    }

    @Test
    void rejectsZeroQuantityExcessDiscountAndPaidAboveTotal() {
        assertThrows(BusinessRuleException.class, () -> SaleMath.quote(
                List.of(new SaleMath.LineInput(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)),
                null, null, null, null));
        assertThrows(BusinessRuleException.class, () -> SaleMath.quote(
                List.of(new SaleMath.LineInput(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("2"), BigDecimal.ZERO)),
                null, null, null, null));
        assertThrows(BusinessRuleException.class, () -> SaleMath.quote(
                List.of(new SaleMath.LineInput(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)),
                null, null, null, new BigDecimal("2")));
    }
}
