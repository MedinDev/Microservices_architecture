package com.microservices.common.dto;

import java.math.BigDecimal;

public record PaymentEventPayload(
    Long paymentId,
    Long orderId,
    Long userId,
    String status,
    BigDecimal amount,
    String transactionReference,
    String failureReason
) {
}
