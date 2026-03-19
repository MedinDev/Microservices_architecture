package com.microservices.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.common.dto.OrderEventPayload;
import com.microservices.common.dto.PaymentEventPayload;
import com.microservices.common.event.DomainEvent;
import com.microservices.common.event.EventType;
import com.microservices.common.exception.BusinessValidationException;
import com.microservices.common.exception.ResourceNotFoundException;
import com.microservices.common.kafka.KafkaTopics;
import com.microservices.common.security.DataMaskingUtil;
import com.microservices.common.tracing.CorrelationIdUtil;
import com.microservices.payment.domain.PaymentEntity;
import com.microservices.payment.domain.PaymentStatus;
import com.microservices.payment.domain.PaymentTransactionLogEntity;
import com.microservices.payment.domain.ProcessedPaymentEventEntity;
import com.microservices.payment.domain.RefundEntity;
import com.microservices.payment.dto.PaymentResponse;
import com.microservices.payment.dto.ProcessPaymentRequest;
import com.microservices.payment.dto.RefundRequest;
import com.microservices.payment.repository.PaymentRepository;
import com.microservices.payment.repository.PaymentTransactionLogRepository;
import com.microservices.payment.repository.ProcessedPaymentEventRepository;
import com.microservices.payment.repository.RefundRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionLogRepository transactionLogRepository;
    private final RefundRepository refundRepository;
    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RedisLockRegistry lockRegistry;
    private final PaymentGatewayClient paymentGatewayClient;
    private final ProcessedPaymentEventRepository processedPaymentEventRepository;
    private final Map<Long, PaymentResponse> paymentReadCache;
    private final Map<Long, List<PaymentResponse>> orderPaymentReadCache;
    private final int maxReadCacheEntries;
    private final int transactionLogRetentionDays;
    private final int refundRetentionDays;
    private final int processedEventRetentionDays;
    private final Counter paymentsProcessedCounter;
    private final Counter paymentsFailedCounter;
    private final Counter refundsProcessedCounter;
    private final DistributionSummary paymentAmountSummary;
    private final Timer processPaymentTimer;

    public PaymentService(
        PaymentRepository paymentRepository,
        PaymentTransactionLogRepository transactionLogRepository,
        RefundRepository refundRepository,
        KafkaTemplate<String, DomainEvent> kafkaTemplate,
        ObjectMapper objectMapper,
        RedisLockRegistry lockRegistry,
        PaymentGatewayClient paymentGatewayClient,
        ProcessedPaymentEventRepository processedPaymentEventRepository,
        MeterRegistry meterRegistry,
        @Value("${app.cache.max-read-entries:5000}") int maxReadCacheEntries,
        @Value("${app.lifecycle.transaction-log-retention-days:90}") int transactionLogRetentionDays,
        @Value("${app.lifecycle.refund-retention-days:365}") int refundRetentionDays,
        @Value("${app.lifecycle.processed-event-retention-days:30}") int processedEventRetentionDays
    ) {
        this.paymentRepository = paymentRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.refundRepository = refundRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.lockRegistry = lockRegistry;
        this.paymentGatewayClient = paymentGatewayClient;
        this.processedPaymentEventRepository = processedPaymentEventRepository;
        this.maxReadCacheEntries = maxReadCacheEntries;
        this.transactionLogRetentionDays = transactionLogRetentionDays;
        this.refundRetentionDays = refundRetentionDays;
        this.processedEventRetentionDays = processedEventRetentionDays;
        this.paymentReadCache = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, PaymentResponse> eldest) {
                return size() > PaymentService.this.maxReadCacheEntries;
            }
        });
        this.orderPaymentReadCache = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, List<PaymentResponse>> eldest) {
                return size() > PaymentService.this.maxReadCacheEntries;
            }
        });
        this.paymentsProcessedCounter = Counter.builder("business_payments_processed_total").register(meterRegistry);
        this.paymentsFailedCounter = Counter.builder("business_payments_failed_total").register(meterRegistry);
        this.refundsProcessedCounter = Counter.builder("business_refunds_processed_total").register(meterRegistry);
        this.paymentAmountSummary = DistributionSummary.builder("business_payment_amount").baseUnit("currency").register(meterRegistry);
        this.processPaymentTimer = Timer.builder("business_payment_process_duration").register(meterRegistry);
    }

    @Transactional
    @Retryable(retryFor = RuntimeException.class, maxAttempts = 3, backoff = @Backoff(delay = 300))
    public PaymentResponse processPayment(ProcessPaymentRequest request) {
        long startTime = System.nanoTime();
        try {
            return paymentRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(this::toResponse)
                .orElseGet(() -> processNewPayment(request));
        } finally {
            processPaymentTimer.record(Duration.ofNanos(System.nanoTime() - startTime));
        }
    }

    @Transactional
    @CircuitBreaker(name = "paymentReadService", fallbackMethod = "getPaymentFallback")
    @Retry(name = "paymentReadService")
    @Bulkhead(name = "paymentReadService")
    public PaymentResponse getPayment(Long paymentId) {
        PaymentResponse response = toResponse(fetchPayment(paymentId));
        paymentReadCache.put(paymentId, response);
        return response;
    }

    @Transactional
    @CircuitBreaker(name = "paymentReadService", fallbackMethod = "getOrderPaymentsFallback")
    @Retry(name = "paymentReadService")
    @Bulkhead(name = "paymentReadService")
    public List<PaymentResponse> getOrderPayments(Long orderId) {
        List<PaymentResponse> responses = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream().map(this::toResponse).toList();
        orderPaymentReadCache.put(orderId, responses);
        return responses;
    }

    @Transactional
    public PaymentResponse refund(Long paymentId, RefundRequest request) {
        PaymentEntity payment = fetchPayment(paymentId);
        if (payment.getStatus() != PaymentStatus.PROCESSED) {
            throw new BusinessValidationException("Only processed payments can be refunded");
        }
        RefundEntity refund = new RefundEntity();
        refund.setPaymentId(paymentId);
        refund.setAmount(request.amount());
        refund.setReason(request.reason());
        refund.setCreatedAt(Instant.now());
        refundRepository.save(refund);
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdatedAt(Instant.now());
        paymentRepository.save(payment);
        writeLog(payment.getId(), "REFUND_PROCESSED", request.reason());
        publishPaymentEvent(payment, EventType.REFUND_PROCESSED, CorrelationIdUtil.currentOrNew());
        refundsProcessedCounter.increment();
        return toResponse(payment);
    }

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "payment-service-order-events")
    @Transactional
    public void onOrderEvent(DomainEvent event) {
        if (!(event.eventType() == EventType.ORDER_CREATED || event.eventType() == EventType.ORDER_CANCELLED)) {
            return;
        }
        if (processedPaymentEventRepository.existsByEventId(event.eventId())) {
            return;
        }
        OrderEventPayload payload = objectMapper.convertValue(event.payload(), OrderEventPayload.class);
        if (event.eventType() == EventType.ORDER_CREATED) {
            ProcessPaymentRequest request = new ProcessPaymentRequest(
                payload.orderId(),
                payload.userId(),
                payload.totalAmount(),
                "order-" + payload.orderId() + "-" + event.eventId()
            );
            processPayment(request, event.correlationId());
        } else {
            compensateCancelledOrder(payload.orderId(), event.correlationId());
        }
        markEventProcessed(event.eventId());
    }

    private PaymentResponse processNewPayment(ProcessPaymentRequest request) {
        return processNewPayment(request, CorrelationIdUtil.currentOrNew());
    }

    private PaymentResponse processNewPayment(ProcessPaymentRequest request, String correlationId) {
        Lock lock = lockRegistry.obtain("payment-order-" + request.orderId());
        boolean locked = false;
        try {
            locked = lock.tryLock(5, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessValidationException("Concurrent payment operation in progress");
            }
            PaymentEntity payment = new PaymentEntity();
            payment.setOrderId(request.orderId());
            payment.setUserId(request.userId());
            payment.setAmount(request.amount());
            payment.setStatus(PaymentStatus.PENDING);
            payment.setIdempotencyKey(request.idempotencyKey());
            payment.setCreatedAt(Instant.now());
            payment.setUpdatedAt(Instant.now());
            payment = paymentRepository.save(payment);
            PaymentGatewayClient.GatewayResult gatewayResult;
            try {
                gatewayResult = paymentGatewayClient.authorize(request.orderId(), request.amount()).toCompletableFuture().join();
            } catch (CompletionException ex) {
                gatewayResult = new PaymentGatewayClient.GatewayResult(false, null, "Payment gateway unavailable");
            }
            if (gatewayResult.success()) {
                payment.setStatus(PaymentStatus.PROCESSED);
                payment.setTransactionReference(gatewayResult.transactionReference());
                payment.setFailureReason(null);
                publishPaymentEvent(payment, EventType.PAYMENT_PROCESSED, correlationId);
                writeLog(payment.getId(), "PAYMENT_PROCESSED", payment.getTransactionReference());
                paymentsProcessedCounter.increment();
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(gatewayResult.failureReason());
                publishPaymentEvent(payment, EventType.PAYMENT_FAILED, correlationId);
                writeLog(payment.getId(), "PAYMENT_FAILED", payment.getFailureReason());
                paymentsFailedCounter.increment();
            }
            payment.setUpdatedAt(Instant.now());
            payment = paymentRepository.save(payment);
            paymentAmountSummary.record(payment.getAmount().doubleValue());
            PaymentResponse response = toResponse(payment);
            paymentReadCache.put(response.paymentId(), response);
            orderPaymentReadCache.put(payment.getOrderId(), getOrderPayments(payment.getOrderId()));
            return response;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessValidationException("Unable to acquire distributed lock");
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    private void publishPaymentEvent(PaymentEntity payment, EventType eventType) {
        publishPaymentEvent(payment, eventType, CorrelationIdUtil.currentOrNew());
    }

    private void publishPaymentEvent(PaymentEntity payment, EventType eventType, String correlationId) {
        PaymentEventPayload payload = new PaymentEventPayload(
            payment.getId(),
            payment.getOrderId(),
            payment.getUserId(),
            payment.getStatus().name(),
            payment.getAmount(),
            payment.getTransactionReference(),
            payment.getFailureReason()
        );
        DomainEvent event = new DomainEvent(
            UUID.randomUUID(),
            eventType,
            "PAYMENT",
            String.valueOf(payment.getId()),
            "payment-service",
            Instant.now(),
            correlationId,
            objectMapper.convertValue(payload, Map.class)
        );
        kafkaTemplate.executeInTransaction(
            operations -> operations.send(KafkaTopics.PAYMENT_EVENTS, String.valueOf(payment.getOrderId()), event)
        );
    }

    private void writeLog(Long paymentId, String action, String details) {
        PaymentTransactionLogEntity log = new PaymentTransactionLogEntity();
        log.setPaymentId(paymentId);
        log.setAction(action);
        log.setDetails(DataMaskingUtil.maskIdentifier(details));
        log.setCreatedAt(Instant.now());
        transactionLogRepository.save(log);
    }

    private PaymentEntity fetchPayment(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }

    private void markEventProcessed(UUID eventId) {
        ProcessedPaymentEventEntity processedEvent = new ProcessedPaymentEventEntity();
        processedEvent.setEventId(eventId);
        processedEvent.setConsumerGroup("payment-service-order-events");
        processedEvent.setProcessedAt(Instant.now());
        processedPaymentEventRepository.save(processedEvent);
    }

    private PaymentResponse processPayment(ProcessPaymentRequest request, String correlationId) {
        return paymentRepository.findByIdempotencyKey(request.idempotencyKey())
            .map(this::toResponse)
            .orElseGet(() -> processNewPayment(request, correlationId));
    }

    private void compensateCancelledOrder(Long orderId, String correlationId) {
        PaymentEntity payment = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PROCESSED) {
            return;
        }
        RefundEntity refund = new RefundEntity();
        refund.setPaymentId(payment.getId());
        refund.setAmount(payment.getAmount());
        refund.setReason("Saga compensation for cancelled order " + orderId);
        refund.setCreatedAt(Instant.now());
        refundRepository.save(refund);
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdatedAt(Instant.now());
        payment.setFailureReason("Compensated due to order cancellation");
        paymentRepository.save(payment);
        writeLog(payment.getId(), "SAGA_COMPENSATION_REFUND", "Order cancelled: " + orderId);
        publishPaymentEvent(payment, EventType.REFUND_PROCESSED, correlationId);
        refundsProcessedCounter.increment();
    }

    private PaymentResponse getPaymentFallback(Long paymentId, Throwable throwable) {
        return paymentReadCache.getOrDefault(
            paymentId,
            new PaymentResponse(
                null,
                null,
                null,
                BigDecimal.ZERO,
                PaymentStatus.PENDING,
                null,
                "payment data temporarily unavailable",
                Instant.now(),
                Instant.now()
            )
        );
    }

    private List<PaymentResponse> getOrderPaymentsFallback(Long orderId, Throwable throwable) {
        return orderPaymentReadCache.getOrDefault(orderId, List.of());
    }

    @Scheduled(cron = "${app.lifecycle.cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void cleanupHistoricalData() {
        Instant now = Instant.now();
        transactionLogRepository.deleteByCreatedAtBefore(now.minus(transactionLogRetentionDays, ChronoUnit.DAYS));
        refundRepository.deleteByCreatedAtBefore(now.minus(refundRetentionDays, ChronoUnit.DAYS));
        processedPaymentEventRepository.deleteByProcessedAtBefore(now.minus(processedEventRetentionDays, ChronoUnit.DAYS));
    }

    private PaymentResponse toResponse(PaymentEntity payment) {
        return new PaymentResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getStatus(),
            DataMaskingUtil.maskIdentifier(payment.getTransactionReference()),
            payment.getFailureReason(),
            payment.getCreatedAt(),
            payment.getUpdatedAt()
        );
    }
}
