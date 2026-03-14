package com.microservices.common.event;

public enum EventType {
    ORDER_CREATED,
    ORDER_CANCELLED,
    INVENTORY_RELEASED,
    PAYMENT_PROCESSED,
    PAYMENT_FAILED,
    REFUND_PROCESSED,
    NOTIFICATION_SENT
}
