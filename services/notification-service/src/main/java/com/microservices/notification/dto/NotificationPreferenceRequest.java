package com.microservices.notification.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceRequest(
    @NotNull Long userId,
    @NotNull Boolean emailEnabled,
    @NotNull Boolean smsEnabled,
    @NotNull Boolean pushEnabled,
    @NotNull Boolean inAppEnabled
) {
}
