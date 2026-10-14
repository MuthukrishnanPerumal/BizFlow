package com.aveline.bizflow.sale;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.finance.FinanceMath;
import com.aveline.bizflow.master.MasterText;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class SaleMath {

    public static final BigDecimal ZERO = FinanceMath.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private SaleMath() {
    }

    public record LineInput(
            BigDecimal quantity,
            BigDecimal sellingPrice,
            BigDecimal discount,
            BigDecimal taxPercent) {
    }

    public record LineQuote(
            BigDecimal gross,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal total) {
    }

    public record DocumentQuote(
            BigDecimal subtotal,
            BigDecimal itemDiscount,
            BigDecimal orderDiscount,
            BigDecimal totalDiscount,
            BigDecimal tax,
            BigDecimal additionalCharges,
            BigDecimal roundOff,
            BigDecimal grandTotal,
            BigDecimal paidAmount,
            BigDecimal balanceAmount,
            List<LineQuote> lines) {
    }

    public static DocumentQuote quote(
            List<LineInput> lines,
            BigDecimal orderDiscount,
            BigDecimal additionalCharges,
            BigDecimal roundOff,
            BigDecimal paidAmount) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleException("REQUIRED", "items", "Add at least one sale line");
        }
        List<LineQuote> quotes = new ArrayList<>();
        BigDecimal subtotal = ZERO;
        BigDecimal itemDiscount = ZERO;
        BigDecimal tax = ZERO;
        for (int index = 0; index < lines.size(); index++) {
            LineQuote quote = line(lines.get(index), index);
            quotes.add(quote);
            subtotal = subtotal.add(quote.gross());
            itemDiscount = itemDiscount.add(quote.discount());
            tax = tax.add(quote.tax());
        }
        BigDecimal order = moneyOrZero(orderDiscount, "orderDiscount", "Order discount");
        BigDecimal charges = moneyOrZero(additionalCharges, "additionalCharges", "Additional charges");
        BigDecimal round = moneyOrZero(roundOff, "roundOff", "Round off");
        BigDecimal paid = moneyOrZero(paidAmount, "paidAmount", "Paid amount");
        if (order.signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_AMOUNT", "orderDiscount", "Order discount cannot be negative");
        }
        if (charges.signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_AMOUNT", "additionalCharges", "Additional charges cannot be negative");
        }
        BigDecimal netGoods = subtotal.subtract(itemDiscount);
        if (order.compareTo(netGoods) > 0) {
            throw new BusinessRuleException(
                    "DISCOUNT",
                    "orderDiscount",
                    "Order discount cannot be greater than the goods value after line discounts");
        }
        FinanceMath.requireRoundOff(round, "roundOff");
        BigDecimal totalDiscount = itemDiscount.add(order);
        BigDecimal grand = FinanceMath.documentTotal(subtotal, totalDiscount, tax, charges, round);
        if (grand.signum() < 0) {
            throw new BusinessRuleException("TOTAL", "grandTotal", "Grand total cannot be negative");
        }
        if (paid.signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_AMOUNT", "paidAmount", "Paid amount cannot be negative");
        }
        if (paid.compareTo(grand) > 0) {
            throw new BusinessRuleException("PAID_AMOUNT", "paidAmount", "Paid amount cannot be greater than the grand total");
        }
        return new DocumentQuote(
                subtotal,
                itemDiscount,
                order,
                totalDiscount,
                tax,
                charges,
                round,
                grand,
                paid,
                FinanceMath.balance(grand, paid),
                List.copyOf(quotes));
    }

    public static BigDecimal ratio(BigDecimal amount, BigDecimal part, BigDecimal whole) {
        return FinanceMath.ratio(amount, part, whole);
    }

    public static BigDecimal clamp(BigDecimal value) {
        return value.signum() < 0 ? ZERO : value;
    }

    private static LineQuote line(LineInput input, int index) {
        String prefix = "items[" + index + "].";
        BigDecimal quantity = MasterText.money(input.quantity(), prefix + "quantity", "Quantity", true);
        BigDecimal price = MasterText.money(input.sellingPrice(), prefix + "sellingPrice", "Selling price", true);
        BigDecimal discount = input.discount() == null
                ? ZERO
                : MasterText.money(input.discount(), prefix + "discount", "Discount", true);
        BigDecimal taxPercent = MasterText.money(input.taxPercent(), prefix + "tax", "Tax", true);
        if (quantity.signum() <= 0) {
            throw new BusinessRuleException("QUANTITY", prefix + "quantity", "Quantity must be greater than zero");
        }
        if (price.signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_AMOUNT", prefix + "sellingPrice", "Selling price cannot be negative");
        }
        if (discount.signum() < 0) {
            throw new BusinessRuleException("DISCOUNT", prefix + "discount", "Discount cannot be negative");
        }
        if (taxPercent.signum() < 0 || taxPercent.compareTo(HUNDRED) > 0) {
            throw new BusinessRuleException("TAX_RANGE", prefix + "tax", "Tax must be between 0 and 100 percent");
        }
        BigDecimal gross = FinanceMath.multiply(quantity, price);
        if (discount.compareTo(gross) > 0) {
            throw new BusinessRuleException("DISCOUNT", prefix + "discount", "Discount cannot be greater than the line amount");
        }
        BigDecimal taxable = gross.subtract(discount);
        BigDecimal tax = FinanceMath.tax(taxable, taxPercent);
        BigDecimal total = taxable.add(tax);
        return new LineQuote(gross, discount, tax, total);
    }

    private static BigDecimal moneyOrZero(BigDecimal value, String field, String label) {
        if (value == null) {
            return ZERO;
        }
        return MasterText.money(value, field, label, true);
    }
}
