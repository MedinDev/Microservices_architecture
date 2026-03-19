package com.microservices.payment.repository;

import com.microservices.payment.domain.RefundEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<RefundEntity, Long> {
    void deleteByCreatedAtBefore(Instant cutoff);
}
