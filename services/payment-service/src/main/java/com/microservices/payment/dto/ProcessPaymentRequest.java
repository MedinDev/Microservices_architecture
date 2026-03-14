package com.microservices.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProcessPaymentRequest(
    @NotNull Long orderId,
    @NotNull Long userId,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @NotBlank String idempotencyKey
) {
}
