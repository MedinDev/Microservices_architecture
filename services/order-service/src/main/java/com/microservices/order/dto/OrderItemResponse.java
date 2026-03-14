package com.microservices.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
    Long id,
    String productCode,
    Integer quantity,
    BigDecimal unitPrice
) {
}
