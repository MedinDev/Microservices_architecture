package com.microservices.payment.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microservices.payment.domain.PaymentEntity;
import com.microservices.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PaymentRepositoryPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("payment_test_db")
        .withUsername("payment_user")
        .withPassword("payment_password");

    @Autowired
    private PaymentRepository paymentRepository;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Test
    void supportsConcurrentPaymentWrites() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                int index = i;
                tasks.add(() -> {
                    paymentRepository.save(payment(index));
                    return null;
                });
            }
            executor.invokeAll(tasks);
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            assertEquals(20, paymentRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    private PaymentEntity payment(int index) {
        PaymentEntity entity = new PaymentEntity();
        entity.setOrderId(5000L + index);
        entity.setUserId(100L + index);
        entity.setAmount(BigDecimal.valueOf(30 + index));
        entity.setStatus(PaymentStatus.PENDING);
        entity.setIdempotencyKey("idem-concurrent-" + index);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
