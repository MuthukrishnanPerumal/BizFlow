package com.aveline.bizflow.finance;

public enum ExpenseCategory {
    RENT(GlAccount.RENT),
    UTILITIES(GlAccount.UTILITIES),
    SALARY(GlAccount.SALARY),
    TRANSPORT(GlAccount.TRANSPORT),
    OFFICE(GlAccount.OFFICE),
    MAINTENANCE(GlAccount.MAINTENANCE),
    MARKETING(GlAccount.MARKETING),
    PROFESSIONAL(GlAccount.PROFESSIONAL),
    TAX(GlAccount.TAX_EXPENSE),
    OTHER(GlAccount.OTHER_EXPENSE);

    private final GlAccount account;

    ExpenseCategory(GlAccount account) {
        this.account = account;
    }

    public GlAccount account() {
        return account;
    }
}
