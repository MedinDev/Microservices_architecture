package com.microservices.payment.dto;

import com.microservices.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
    Long paymentId,
    Long orderId,
    Long userId,
    BigDecimal amount,
    PaymentStatus status,
    String transactionReference,
    String failureReason,
    Instant createdAt,
    Instant updatedAt
) {
}
