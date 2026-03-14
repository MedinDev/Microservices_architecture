package com.microservices.common.kafka;

public final class KafkaTopics {

    public static final String ORDER_EVENTS = "order.events";
    public static final String PAYMENT_EVENTS = "payment.events";
    public static final String NOTIFICATION_EVENTS = "notification.events";
    public static final String ORDER_EVENTS_DLT = "order.events.dlt";
    public static final String PAYMENT_EVENTS_DLT = "payment.events.dlt";
    public static final String NOTIFICATION_EVENTS_DLT = "notification.events.dlt";

    private KafkaTopics() {
    }
}
