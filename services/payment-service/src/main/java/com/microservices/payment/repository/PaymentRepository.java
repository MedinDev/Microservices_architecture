package com.microservices.payment.repository;

import com.microservices.payment.domain.PaymentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);
    List<PaymentEntity> findByOrderIdOrderByCreatedAtDesc(Long orderId);
    Optional<PaymentEntity> findTopByOrderIdOrderByCreatedAtDesc(Long orderId);
}
