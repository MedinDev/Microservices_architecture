package com.microservices.notification.controller;

import com.microservices.common.audit.AuditableAction;
import com.microservices.common.security.RequiredRole;
import com.microservices.notification.dto.NotificationPreferenceRequest;
import com.microservices.notification.dto.NotificationPreferenceResponse;
import com.microservices.notification.dto.NotificationResponse;
import com.microservices.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/user/{userId}")
    @RequiredRole("NOTIFICATION_USER")
    @AuditableAction("NOTIFICATION_LIST_BY_USER")
    public ResponseEntity<List<NotificationResponse>> getUserNotifications(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getUserNotifications(userId));
    }

    @PutMapping("/{id}/read")
    @RequiredRole("NOTIFICATION_USER")
    @AuditableAction("NOTIFICATION_MARK_READ")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    @DeleteMapping("/{id}")
    @RequiredRole("NOTIFICATION_USER")
    @AuditableAction("NOTIFICATION_DELETE")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/preferences")
    @RequiredRole("NOTIFICATION_USER")
    @AuditableAction("NOTIFICATION_PREFERENCES_UPDATE")
    public ResponseEntity<NotificationPreferenceResponse> updatePreferences(
        @Valid @RequestBody NotificationPreferenceRequest request
    ) {
        return ResponseEntity.ok(notificationService.updatePreferences(request));
    }
}
