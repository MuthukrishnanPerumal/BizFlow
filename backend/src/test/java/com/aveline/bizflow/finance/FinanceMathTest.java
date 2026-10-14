package com.aveline.bizflow.finance;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinanceMathTest {

    @Test
    void roundsTaxDiscountAndBalanceOnTheSharedScale() {
        BigDecimal gross = FinanceMath.multiply(new BigDecimal("2"), new BigDecimal("10.3333"));
        assertEquals(new BigDecimal("20.6666"), gross);
        BigDecimal tax = FinanceMath.tax(new BigDecimal("100.0000"), new BigDecimal("2.5000"));
        assertEquals(new BigDecimal("2.5000"), tax);
        assertEquals(new BigDecimal("90.0000"), FinanceMath.balance(new BigDecimal("100.0000"), new BigDecimal("10.0000")));
        assertEquals(
                new BigDecimal("70.0000"),
                FinanceMath.openReceivable(
                        new BigDecimal("100.0000"),
                        new BigDecimal("20.0000"),
                        new BigDecimal("15.0000"),
                        new BigDecimal("5.0000")));
        assertEquals(
                new BigDecimal("40.0000"),
                FinanceMath.openPayable(new BigDecimal("80.0000"), new BigDecimal("25.0000"), new BigDecimal("15.0000")));
    }

    @Test
    void rejectsNonPositiveMoneyAndAllocatesPartialFullAndAdvanceAmounts() {
        assertThrows(BusinessRuleException.class, () -> FinanceMath.positive(new BigDecimal("0.0000"), "amount", "Amount"));
        assertThrows(BusinessRuleException.class, () -> FinanceMath.positive(new BigDecimal("-1.0000"), "amount", "Amount"));

        FinanceMath.Settlement partial = FinanceMath.allocateFifo(new BigDecimal("30.0000"), List.of(new BigDecimal("80.0000")));
        assertEquals(List.of(new BigDecimal("30.0000")), partial.applied());
        assertEquals(new BigDecimal("0.0000"), partial.unallocated());

        FinanceMath.Settlement full = FinanceMath.allocateFifo(
                new BigDecimal("100.0000"),
                List.of(new BigDecimal("80.0000"), new BigDecimal("40.0000")));
        assertEquals(List.of(new BigDecimal("80.0000"), new BigDecimal("20.0000")), full.applied());
        assertEquals(new BigDecimal("0.0000"), full.unallocated());

        FinanceMath.Settlement advance = FinanceMath.allocateFifo(new BigDecimal("25.0000"), List.of());
        assertEquals(new BigDecimal("25.0000"), advance.unallocated());
    }
}
