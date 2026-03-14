package com.microservices.common.dto;

import java.math.BigDecimal;

public record OrderItemSnapshot(
    String productCode,
    int quantity,
    BigDecimal unitPrice
) {
}
