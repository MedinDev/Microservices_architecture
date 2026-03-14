package com.microservices.common.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderEventPayload(
    Long orderId,
    Long userId,
    String status,
    BigDecimal totalAmount,
    List<OrderItemSnapshot> items
) {
}
