package com.microservices.order.repository;

import com.microservices.order.domain.ProcessedOrderEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedOrderEventRepository extends JpaRepository<ProcessedOrderEventEntity, Long> {
    boolean existsByEventId(UUID eventId);
}
