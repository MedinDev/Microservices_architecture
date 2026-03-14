package com.microservices.common.tracing;

public final class CorrelationIdConstants {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private CorrelationIdConstants() {
    }
}
