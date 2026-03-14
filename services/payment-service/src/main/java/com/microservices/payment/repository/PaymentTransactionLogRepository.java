package com.microservices.payment.repository;

import com.microservices.payment.domain.PaymentTransactionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionLogRepository extends JpaRepository<PaymentTransactionLogEntity, Long> {
}
