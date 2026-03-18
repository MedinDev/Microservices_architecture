package com.microservices.payment.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microservices.payment.domain.PaymentEntity;
import com.microservices.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class PaymentRepositoryDataJpaTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void findByIdempotencyKeyReturnsMatch() {
        paymentRepository.save(payment(44L, "idem-44", Instant.parse("2026-03-10T12:00:00Z")));

        assertTrue(paymentRepository.findByIdempotencyKey("idem-44").isPresent());
    }

    @Test
    void findTopByOrderIdOrderByCreatedAtDescReturnsLatest() {
        paymentRepository.save(payment(77L, "idem-77-a", Instant.parse("2026-03-10T12:00:00Z")));
        paymentRepository.save(payment(77L, "idem-77-b", Instant.parse("2026-03-10T12:05:00Z")));

        PaymentEntity latest = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(77L).orElseThrow();

        assertEquals("idem-77-b", latest.getIdempotencyKey());
    }

    private PaymentEntity payment(Long orderId, String idempotencyKey, Instant createdAt) {
        PaymentEntity entity = new PaymentEntity();
        entity.setOrderId(orderId);
        entity.setUserId(5L);
        entity.setAmount(BigDecimal.valueOf(18.5));
        entity.setStatus(PaymentStatus.PENDING);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(createdAt);
        return entity;
    }
}
