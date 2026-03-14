package com.microservices.payment.repository;

import com.microservices.payment.domain.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<RefundEntity, Long> {
}
