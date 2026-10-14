package com.aveline.bizflow.finance;

import com.aveline.bizflow.common.exception.BusinessRuleException;

public enum GlAccount {
    CASH("1100", "Cash"),
    BANK("1110", "Bank"),
    CARD("1120", "Card clearing"),
    UPI("1130", "UPI clearing"),
    OTHER_FUNDS("1190", "Other funds"),
    ACCOUNTS_RECEIVABLE("1200", "Accounts receivable"),
    SUPPLIER_ADVANCES("1250", "Supplier advances"),
    ACCOUNTS_PAYABLE("2000", "Accounts payable"),
    CUSTOMER_ADVANCES("2150", "Customer advances"),
    OTHER_EXPENSE("5000", "Other expense"),
    RENT("5100", "Rent"),
    UTILITIES("5200", "Utilities"),
    SALARY("5300", "Salaries"),
    TRANSPORT("5400", "Transport"),
    OFFICE("5500", "Office"),
    MAINTENANCE("5600", "Maintenance"),
    MARKETING("5700", "Marketing"),
    PROFESSIONAL("5800", "Professional fees"),
    TAX_EXPENSE("5900", "Taxes");

    private final String code;
    private final String label;

    GlAccount(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static GlAccount funds(PaymentMethod method) {
        return switch (method) {
            case CASH -> CASH;
            case CARD -> CARD;
            case UPI -> UPI;
            case BANK, BANK_TRANSFER, CHEQUE -> BANK;
            case OTHER -> OTHER_FUNDS;
            case CREDIT -> throw new BusinessRuleException(
                    "PAYMENT_METHOD",
                    "paymentMethod",
                    "Credit is not a settlement account");
        };
    }

    public static GlAccount fromCode(String code) {
        for (GlAccount account : values()) {
            if (account.code.equals(code)) {
                return account;
            }
        }
        throw new IllegalArgumentException("Unknown ledger account " + code);
    }
}
