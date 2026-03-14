package com.microservices.notification.dto;

public record NotificationPreferenceResponse(
    Long userId,
    Boolean emailEnabled,
    Boolean smsEnabled,
    Boolean pushEnabled,
    Boolean inAppEnabled
) {
}
