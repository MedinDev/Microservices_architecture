package com.microservices.notification.repository;

import com.microservices.notification.domain.ProcessedNotificationEventEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedNotificationEventRepository extends JpaRepository<ProcessedNotificationEventEntity, Long> {
    boolean existsByEventId(UUID eventId);
    void deleteByProcessedAtBefore(Instant cutoff);
}
