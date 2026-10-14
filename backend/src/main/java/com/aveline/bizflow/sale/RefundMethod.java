package com.aveline.bizflow.sale;

import com.aveline.bizflow.finance.PaymentMethod;

public enum RefundMethod {
    CASH,
    CARD,
    UPI,
    BANK_TRANSFER,
    CREDIT;

    public boolean settlesInCash() {
        return this != CREDIT;
    }

    public PaymentMethod toPaymentMethod() {
        if (this == CREDIT) {
            throw new IllegalStateException("A credit adjustment is not a cash movement");
        }
        return PaymentMethod.valueOf(name());
    }
}
