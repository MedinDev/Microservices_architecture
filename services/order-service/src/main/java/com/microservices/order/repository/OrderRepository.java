package com.microservices.order.repository;

import com.microservices.order.domain.OrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findById(Long id);

    @EntityGraph(attributePaths = "items")
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
