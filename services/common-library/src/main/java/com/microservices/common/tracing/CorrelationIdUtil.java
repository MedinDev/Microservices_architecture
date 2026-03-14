package com.microservices.common.tracing;

import java.util.UUID;
import org.slf4j.MDC;

public final class CorrelationIdUtil {

    private CorrelationIdUtil() {
    }

    public static String currentOrNew() {
        String correlationId = MDC.get(CorrelationIdConstants.CORRELATION_ID_MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }
}
