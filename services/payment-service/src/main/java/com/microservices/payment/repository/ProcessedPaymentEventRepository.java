package com.microservices.payment.repository;

import com.microservices.payment.domain.ProcessedPaymentEventEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedPaymentEventRepository extends JpaRepository<ProcessedPaymentEventEntity, Long> {
    boolean existsByEventId(UUID eventId);
    void deleteByProcessedAtBefore(Instant cutoff);
}
