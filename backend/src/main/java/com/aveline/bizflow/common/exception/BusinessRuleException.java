package com.aveline.bizflow.common.exception;

public class BusinessRuleException extends RuntimeException {

    private final String code;
    private final String field;

    public BusinessRuleException(String code, String message) {
        this(code, null, message);
    }

    public BusinessRuleException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
