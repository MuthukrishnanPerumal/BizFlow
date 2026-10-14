package com.aveline.bizflow.document;

public enum DocumentSeries {
    PURCHASE("PUR"),
    PURCHASE_RETURN("PRT"),
    SALE("SAL"),
    SALES_RETURN("SRT"),
    PAYMENT("PAY"),
    EXPENSE("EXP"),
    JOURNAL("JRN"),
    ADJUSTMENT("ADJ"),
    TRANSFER("TRF");

    private final String prefix;

    DocumentSeries(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
