package com.microservices.payment.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayClient {

    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "gatewayErrorFallback")
    @Retry(name = "paymentGateway")
    @Bulkhead(name = "paymentGateway", type = Bulkhead.Type.THREADPOOL)
    @TimeLimiter(name = "paymentGateway", fallbackMethod = "gatewayTimeoutFallback")
    public CompletionStage<GatewayResult> authorize(Long orderId, BigDecimal amount) {
        return CompletableFuture.supplyAsync(() -> {
            if (orderId % 11 == 0) {
                try {
                    Thread.sleep(Duration.ofSeconds(2).toMillis());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            if (orderId % 5 == 0) {
                return new GatewayResult(false, null, "Mock gateway rejected transaction");
            }
            return new GatewayResult(true, "txn-" + UUID.randomUUID(), null);
        });
    }

    public CompletionStage<GatewayResult> gatewayErrorFallback(Long orderId, BigDecimal amount, Throwable throwable) {
        return CompletableFuture.completedFuture(
            new GatewayResult(false, null, "Payment gateway unavailable")
        );
    }

    public CompletionStage<GatewayResult> gatewayTimeoutFallback(Long orderId, BigDecimal amount, Throwable throwable) {
        return CompletableFuture.completedFuture(
            new GatewayResult(false, null, "Payment gateway timeout")
        );
    }

    public record GatewayResult(boolean success, String transactionReference, String failureReason) {
    }
}
