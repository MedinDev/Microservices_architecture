package com.microservices.order.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microservices.order.domain.OrderEntity;
import com.microservices.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class OrderRepositoryDataJpaTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void findByUserIdOrderByCreatedAtDescReturnsNewestFirst() {
        orderRepository.save(order(3L, Instant.parse("2026-03-10T10:15:30Z"), BigDecimal.valueOf(25)));
        orderRepository.save(order(3L, Instant.parse("2026-03-10T10:16:30Z"), BigDecimal.valueOf(35)));
        orderRepository.save(order(9L, Instant.parse("2026-03-10T10:17:30Z"), BigDecimal.valueOf(45)));

        List<OrderEntity> results = orderRepository.findByUserIdOrderByCreatedAtDesc(3L);

        assertEquals(2, results.size());
        assertEquals(BigDecimal.valueOf(35), results.get(0).getTotalAmount());
        assertEquals(BigDecimal.valueOf(25), results.get(1).getTotalAmount());
    }

    private OrderEntity order(Long userId, Instant createdAt, BigDecimal amount) {
        OrderEntity entity = new OrderEntity();
        entity.setUserId(userId);
        entity.setStatus(OrderStatus.PAYMENT_PENDING);
        entity.setTotalAmount(amount);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(createdAt);
        return entity;
    }
}
