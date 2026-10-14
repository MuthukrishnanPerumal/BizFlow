package com.aveline.bizflow.sale;

import com.aveline.bizflow.finance.PaymentMethod;

public enum SaleTenderMethod {
    CASH,
    CARD,
    UPI,
    BANK_TRANSFER;

    public PaymentMethod toPaymentMethod() {
        return PaymentMethod.valueOf(name());
    }
}
