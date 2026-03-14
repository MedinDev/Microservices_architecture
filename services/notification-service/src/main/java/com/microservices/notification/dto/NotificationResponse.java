package com.microservices.notification.dto;

import com.microservices.notification.domain.NotificationChannel;
import com.microservices.notification.domain.NotificationStatus;
import java.time.Instant;

public record NotificationResponse(
    Long id,
    Long userId,
    String title,
    String message,
    NotificationChannel channel,
    NotificationStatus status,
    Boolean read,
    Instant createdAt,
    Instant sentAt
) {
}
