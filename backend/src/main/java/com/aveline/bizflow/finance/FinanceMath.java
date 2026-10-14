package com.aveline.bizflow.finance;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.master.MasterText;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Single money kernel for the ledger. Document engines and settlement both use these
 * operations so tax, discount, rounding, balances, and allocations share one scale.
 */
public final class FinanceMath {

    public static final int SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE = new BigDecimal("1.0000");

    private FinanceMath() {
    }

    public static BigDecimal money(BigDecimal value, String field, String label) {
        return MasterText.money(value, field, label, true);
    }

    public static BigDecimal positive(BigDecimal value, String field, String label) {
        BigDecimal amount = money(value, field, label);
        if (amount.signum() <= 0) {
            throw new BusinessRuleException("AMOUNT", field, label + " must be greater than zero");
        }
        return amount;
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    public static BigDecimal multiply(BigDecimal left, BigDecimal right) {
        return left.multiply(right).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal tax(BigDecimal taxable, BigDecimal percent) {
        return taxable.multiply(percent).divide(HUNDRED, SCALE, ROUNDING);
    }

    public static BigDecimal ratio(BigDecimal amount, BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return ZERO;
        }
        return amount.multiply(part).divide(whole, SCALE, ROUNDING);
    }

    public static void requireRoundOff(BigDecimal roundOff, String field) {
        if (roundOff.abs().compareTo(ONE) > 0) {
            throw new BusinessRuleException("ROUND_OFF", field, "Round off must be between -1 and 1");
        }
    }

    public static BigDecimal documentTotal(
            BigDecimal subtotal,
            BigDecimal totalDiscount,
            BigDecimal tax,
            BigDecimal additionalCharges,
            BigDecimal roundOff) {
        return subtotal.subtract(totalDiscount).add(tax).add(additionalCharges).add(roundOff).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal balance(BigDecimal grandTotal, BigDecimal paidAmount) {
        return grandTotal.subtract(paidAmount);
    }

    public static BigDecimal openReceivable(
            BigDecimal grandTotal,
            BigDecimal paidAmount,
            BigDecimal returnedValue,
            BigDecimal refundedAmount) {
        BigDecimal open = grandTotal
                .subtract(zeroIfNull(paidAmount))
                .subtract(zeroIfNull(returnedValue))
                .add(zeroIfNull(refundedAmount));
        return open.signum() < 0 ? ZERO : open;
    }

    public static BigDecimal openPayable(BigDecimal grandTotal, BigDecimal paidAmount, BigDecimal returnedValue) {
        BigDecimal open = grandTotal.subtract(zeroIfNull(paidAmount)).subtract(zeroIfNull(returnedValue));
        return open.signum() < 0 ? ZERO : open;
    }

    public record Settlement(List<BigDecimal> applied, BigDecimal unallocated) {
    }

    public static Settlement allocateFifo(BigDecimal payment, List<BigDecimal> openBalances) {
        BigDecimal remaining = positive(payment, "amount", "Amount");
        List<BigDecimal> applied = new ArrayList<>();
        for (BigDecimal open : openBalances) {
            BigDecimal room = zeroIfNull(open);
            if (room.signum() < 0) {
                throw new BusinessRuleException("ALLOCATION", "allocations", "An open balance cannot be negative");
            }
            BigDecimal take = remaining.min(room);
            applied.add(take);
            remaining = remaining.subtract(take);
        }
        return new Settlement(List.copyOf(applied), remaining);
    }
}
