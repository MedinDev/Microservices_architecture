package com.microservices.order.domain;

public enum OrderStatus {
    PENDING,
    CREATED,
    PAYMENT_PENDING,
    PAYMENT_PROCESSED,
    PAYMENT_FAILED,
    CANCELLED
}
