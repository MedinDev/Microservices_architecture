package com.microservices.order.repository;

import com.microservices.order.domain.OrderOutboxEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEntity, Long> {
    List<OrderOutboxEntity> findTop100ByPublishedFalseOrderByCreatedAtAsc();
    void deleteByPublishedIsTrueAndPublishedAtBefore(Instant cutoff);
}
