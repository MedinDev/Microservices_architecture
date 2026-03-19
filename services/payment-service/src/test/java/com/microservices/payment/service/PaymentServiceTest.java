package com.microservices.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.common.event.DomainEvent;
import com.microservices.common.event.EventType;
import com.microservices.common.exception.BusinessValidationException;
import com.microservices.payment.domain.PaymentEntity;
import com.microservices.payment.domain.PaymentStatus;
import com.microservices.payment.dto.PaymentResponse;
import com.microservices.payment.dto.ProcessPaymentRequest;
import com.microservices.payment.dto.RefundRequest;
import com.microservices.payment.repository.PaymentRepository;
import com.microservices.payment.repository.PaymentTransactionLogRepository;
import com.microservices.payment.repository.ProcessedPaymentEventRepository;
import com.microservices.payment.repository.RefundRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private RefundRepository refundRepository;
    private ProcessedPaymentEventRepository processedPaymentEventRepository;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = Mockito.mock(PaymentRepository.class);
        PaymentTransactionLogRepository transactionLogRepository = Mockito.mock(PaymentTransactionLogRepository.class);
        refundRepository = Mockito.mock(RefundRepository.class);
        ProducerFactory<String, DomainEvent> producerFactory = Mockito.mock(ProducerFactory.class);
        KafkaTemplate<String, DomainEvent> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        RedisConnectionFactory redisConnectionFactory = Mockito.mock(RedisConnectionFactory.class);
        RedisLockRegistry lockRegistry = new RedisLockRegistry(redisConnectionFactory, "phase5-payment-locks");
        PaymentGatewayClient paymentGatewayClient = new PaymentGatewayClient();
        processedPaymentEventRepository = Mockito.mock(ProcessedPaymentEventRepository.class);

        paymentService = new PaymentService(
            paymentRepository,
            transactionLogRepository,
            refundRepository,
            kafkaTemplate,
            new ObjectMapper(),
            lockRegistry,
            paymentGatewayClient,
            processedPaymentEventRepository,
            new SimpleMeterRegistry(),
            5000,
            90,
            365,
            30
        );
    }

    @Test
    void processPaymentReturnsExistingIdempotentPayment() {
        PaymentEntity existing = new PaymentEntity();
        existing.setOrderId(501L);
        existing.setUserId(22L);
        existing.setAmount(BigDecimal.valueOf(49.9));
        existing.setStatus(PaymentStatus.PROCESSED);
        existing.setIdempotencyKey("idem-501");
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        ProcessPaymentRequest request = new ProcessPaymentRequest(501L, 22L, BigDecimal.valueOf(49.9), "idem-501");
        when(paymentRepository.findByIdempotencyKey("idem-501")).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.processPayment(request);

        assertEquals(PaymentStatus.PROCESSED, response.status());
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
    }

    @Test
    void refundThrowsWhenPaymentNotProcessed() {
        PaymentEntity pending = new PaymentEntity();
        pending.setStatus(PaymentStatus.PENDING);
        pending.setAmount(BigDecimal.valueOf(25));
        pending.setCreatedAt(Instant.now());
        when(paymentRepository.findById(77L)).thenReturn(Optional.of(pending));

        assertThrows(
            BusinessValidationException.class,
            () -> paymentService.refund(77L, new RefundRequest(BigDecimal.valueOf(10), "requested"))
        );

        verify(refundRepository, never()).save(any());
    }

    @Test
    void onOrderEventSkipsReplayWhenAlreadyProcessed() {
        DomainEvent event = new DomainEvent(
            UUID.randomUUID(),
            EventType.ORDER_CREATED,
            "ORDER",
            "100",
            "order-service",
            Instant.now(),
            "corr-1",
            Map.of("orderId", 100, "userId", 10, "status", "PAYMENT_PENDING", "totalAmount", 20)
        );
        when(processedPaymentEventRepository.existsByEventId(event.eventId())).thenReturn(true);

        paymentService.onOrderEvent(event);

        verify(paymentRepository, never()).save(any(PaymentEntity.class));
        verify(refundRepository, never()).save(any());
    }
}
