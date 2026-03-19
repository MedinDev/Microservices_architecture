package com.microservices.notification.repository;

import com.microservices.notification.domain.NotificationEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteByReadFlagIsTrueAndCreatedAtBefore(Instant cutoff);
}
