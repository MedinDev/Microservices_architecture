package com.microservices.payment.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PaymentGatewayChaosTest {

    private final PaymentGatewayClient client = new PaymentGatewayClient();

    @Test
    void gatewayRejectsSpecificOrders() throws Exception {
        PaymentGatewayClient.GatewayResult result = client.authorize(25L, BigDecimal.valueOf(40))
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertNotNull(result.failureReason());
    }

    @Test
    void gatewayFallbackHandlesErrorInjection() throws Exception {
        PaymentGatewayClient.GatewayResult result = client.gatewayErrorFallback(
            31L,
            BigDecimal.valueOf(12),
            new RuntimeException("service-terminated")
        ).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertTrue(result.failureReason().contains("unavailable"));
    }

    @Test
    void gatewayTimeoutFallbackHandlesLatencyInjection() throws Exception {
        PaymentGatewayClient.GatewayResult result = client.gatewayTimeoutFallback(
            33L,
            BigDecimal.valueOf(8),
            new RuntimeException("latency-spike")
        ).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertTrue(result.failureReason().contains("timeout"));
    }
}
