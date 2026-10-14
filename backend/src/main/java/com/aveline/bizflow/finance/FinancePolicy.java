package com.aveline.bizflow.finance;

import com.aveline.bizflow.common.exception.BusinessRuleException;

public final class FinancePolicy {

    private FinancePolicy() {
    }

    public static void requireSettlementMethod(PaymentMethod method) {
        if (method != PaymentMethod.CASH
                && method != PaymentMethod.CARD
                && method != PaymentMethod.UPI
                && method != PaymentMethod.BANK_TRANSFER
                && method != PaymentMethod.OTHER) {
            throw new BusinessRuleException(
                    "PAYMENT_METHOD",
                    "paymentMethod",
                    "Choose cash, card, UPI, bank transfer, or other");
        }
    }

    public static final String CUSTOMER_PAYMENT = "CUSTOMER_PAYMENT";
    public static final String SUPPLIER_PAYMENT = "SUPPLIER_PAYMENT";
    public static final String EXPENSE = "EXPENSE";
}
