package com.microservices.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record OrderItemRequest(
    @NotBlank String productCode,
    @NotNull @Min(1) Integer quantity,
    @NotNull @DecimalMin(value = "0.01") BigDecimal unitPrice
) {
}
