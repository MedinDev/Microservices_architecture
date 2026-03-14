package com.microservices.common.dto;

import java.time.Instant;

public record NotificationEventPayload(
    Long notificationId,
    Long userId,
    String channel,
    String title,
    String status,
    Instant sentAt
) {
}
