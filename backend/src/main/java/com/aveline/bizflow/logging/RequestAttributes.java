package com.aveline.bizflow.logging;

public final class RequestAttributes {

    public static final String CORRELATION_ID = "correlationId";
    public static final String ACTOR = "bf.actor";
    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    private RequestAttributes() {
    }
}
