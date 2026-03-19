package com.microservices.payment.repository;

import com.microservices.payment.domain.PaymentTransactionLogEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionLogRepository extends JpaRepository<PaymentTransactionLogEntity, Long> {
    void deleteByCreatedAtBefore(Instant cutoff);
}
