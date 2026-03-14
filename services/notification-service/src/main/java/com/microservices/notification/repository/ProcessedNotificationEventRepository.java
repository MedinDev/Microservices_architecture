package com.microservices.notification.repository;

import com.microservices.notification.domain.ProcessedNotificationEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedNotificationEventRepository extends JpaRepository<ProcessedNotificationEventEntity, Long> {
    boolean existsByEventId(UUID eventId);
}
