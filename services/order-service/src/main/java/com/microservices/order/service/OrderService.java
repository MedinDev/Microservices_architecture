package com.microservices.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.common.dto.OrderEventPayload;
import com.microservices.common.dto.OrderItemSnapshot;
import com.microservices.common.dto.PaymentEventPayload;
import com.microservices.common.event.DomainEvent;
import com.microservices.common.event.EventType;
import com.microservices.common.exception.BusinessValidationException;
import com.microservices.common.exception.ResourceNotFoundException;
import com.microservices.common.kafka.KafkaTopics;
import com.microservices.common.tracing.CorrelationIdUtil;
import com.microservices.order.domain.OrderEntity;
import com.microservices.order.domain.OrderItemEntity;
import com.microservices.order.domain.OrderOutboxEntity;
import com.microservices.order.domain.OrderStatus;
import com.microservices.order.domain.ProcessedOrderEventEntity;
import com.microservices.order.dto.CreateOrderRequest;
import com.microservices.order.dto.OrderItemResponse;
import com.microservices.order.dto.OrderResponse;
import com.microservices.order.repository.OrderOutboxRepository;
import com.microservices.order.repository.ProcessedOrderEventRepository;
import com.microservices.order.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final InventoryServiceClient inventoryServiceClient;
    private final ProcessedOrderEventRepository processedOrderEventRepository;
    private final Counter ordersCreatedCounter;
    private final Counter ordersCancelledCounter;
    private final DistributionSummary orderAmountSummary;
    private final Timer createOrderTimer;

    public OrderService(
        OrderRepository orderRepository,
        OrderOutboxRepository outboxRepository,
        KafkaTemplate<String, DomainEvent> kafkaTemplate,
        ObjectMapper objectMapper,
        InventoryServiceClient inventoryServiceClient,
        ProcessedOrderEventRepository processedOrderEventRepository,
        MeterRegistry meterRegistry
    ) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.inventoryServiceClient = inventoryServiceClient;
        this.processedOrderEventRepository = processedOrderEventRepository;
        this.ordersCreatedCounter = Counter.builder("business_orders_created_total").register(meterRegistry);
        this.ordersCancelledCounter = Counter.builder("business_orders_cancelled_total").register(meterRegistry);
        this.orderAmountSummary = DistributionSummary.builder("business_order_total_amount").baseUnit("currency").register(meterRegistry);
        this.createOrderTimer = Timer.builder("business_order_create_duration").register(meterRegistry);
    }

    @Transactional
    @CacheEvict(cacheNames = "user-orders", key = "#request.userId")
    public OrderResponse createOrder(CreateOrderRequest request) {
        long startTime = System.nanoTime();
        try {
            validateOrder(request);
            OrderEntity order = new OrderEntity();
            order.setUserId(request.userId());
            order.setStatus(OrderStatus.PAYMENT_PENDING);
            order.setCreatedAt(Instant.now());
            order.setUpdatedAt(Instant.now());
            BigDecimal total = request.items().stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            order.setTotalAmount(total);
            request.items().forEach(item -> {
                OrderItemEntity orderItem = new OrderItemEntity();
                orderItem.setOrder(order);
                orderItem.setProductCode(item.productCode());
                orderItem.setQuantity(item.quantity());
                orderItem.setUnitPrice(item.unitPrice());
                order.getItems().add(orderItem);
            });
            OrderEntity saved = orderRepository.save(order);
            enqueueOutbox(saved, EventType.ORDER_CREATED);
            ordersCreatedCounter.increment();
            orderAmountSummary.record(total.doubleValue());
            return toResponse(saved);
        } finally {
            createOrderTimer.record(Duration.ofNanos(System.nanoTime() - startTime));
        }
    }

    @Cacheable(cacheNames = "orders", key = "#orderId")
    public OrderResponse getOrderById(Long orderId) {
        return toResponse(fetchOrder(orderId));
    }

    @Cacheable(cacheNames = "user-orders", key = "#userId")
    public List<OrderResponse> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    public OrderStatus getOrderStatus(Long orderId) {
        return fetchOrder(orderId).getStatus();
    }

    @Transactional
    @CacheEvict(cacheNames = {"orders", "user-orders"}, allEntries = true)
    public OrderResponse cancelOrder(Long orderId) {
        OrderEntity order = fetchOrder(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(order);
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        OrderEntity saved = orderRepository.save(order);
        enqueueOutbox(saved, EventType.ORDER_CANCELLED);
        enqueueOutbox(saved, EventType.INVENTORY_RELEASED);
        ordersCancelledCounter.increment();
        return toResponse(saved);
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS, groupId = "order-service-payment-events")
    @Transactional
    @CacheEvict(cacheNames = {"orders", "user-orders"}, allEntries = true)
    public void consumePaymentEvents(DomainEvent event) {
        if (!(event.eventType() == EventType.PAYMENT_PROCESSED || event.eventType() == EventType.PAYMENT_FAILED)) {
            return;
        }
        if (processedOrderEventRepository.existsByEventId(event.eventId())) {
            return;
        }
        PaymentEventPayload payload = objectMapper.convertValue(event.payload(), PaymentEventPayload.class);
        OrderEntity order = fetchOrder(payload.orderId());
        if (event.eventType() == EventType.PAYMENT_PROCESSED) {
            order.setStatus(OrderStatus.PAYMENT_PROCESSED);
        } else {
            order.setStatus(OrderStatus.CANCELLED);
            enqueueOutbox(order, EventType.ORDER_CANCELLED, event.correlationId());
            enqueueOutbox(order, EventType.INVENTORY_RELEASED, event.correlationId());
        }
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        markEventProcessed(event.eventId());
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishOutbox() {
        List<OrderOutboxEntity> pending = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        for (OrderOutboxEntity outbox : pending) {
            try {
                DomainEvent event = objectMapper.readValue(outbox.getPayload(), DomainEvent.class);
                kafkaTemplate.executeInTransaction(
                    operations -> operations.send(KafkaTopics.ORDER_EVENTS, String.valueOf(outbox.getOrderId()), event)
                );
                outbox.setPublished(true);
                outbox.setPublishedAt(Instant.now());
                outboxRepository.save(outbox);
            } catch (Exception ex) {
                break;
            }
        }
    }

    private void validateOrder(CreateOrderRequest request) {
        boolean inventoryAvailable = inventoryServiceClient.reserveInventory(request.items());
        if (!inventoryAvailable) {
            throw new BusinessValidationException("Inventory unavailable for one or more items");
        }
    }

    private OrderEntity fetchOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    private void enqueueOutbox(OrderEntity order, EventType eventType) {
        enqueueOutbox(order, eventType, CorrelationIdUtil.currentOrNew());
    }

    private void enqueueOutbox(OrderEntity order, EventType eventType, String correlationId) {
        OrderEventPayload payload = new OrderEventPayload(
            order.getId(),
            order.getUserId(),
            order.getStatus().name(),
            order.getTotalAmount(),
            order.getItems().stream()
                .map(item -> new OrderItemSnapshot(item.getProductCode(), item.getQuantity(), item.getUnitPrice()))
                .toList()
        );
        DomainEvent event = new DomainEvent(
            UUID.randomUUID(),
            eventType,
            "ORDER",
            String.valueOf(order.getId()),
            "order-service",
            Instant.now(),
            correlationId,
            objectMapper.convertValue(payload, Map.class)
        );
        OrderOutboxEntity outbox = new OrderOutboxEntity();
        outbox.setOrderId(order.getId());
        outbox.setEventType(eventType);
        outbox.setPublished(false);
        outbox.setCreatedAt(Instant.now());
        try {
            outbox.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            throw new BusinessValidationException("Unable to serialize domain event");
        }
        outboxRepository.save(outbox);
    }

    private void markEventProcessed(UUID eventId) {
        ProcessedOrderEventEntity processedEvent = new ProcessedOrderEventEntity();
        processedEvent.setEventId(eventId);
        processedEvent.setConsumerGroup("order-service-payment-events");
        processedEvent.setProcessedAt(Instant.now());
        processedOrderEventRepository.save(processedEvent);
    }

    private OrderResponse toResponse(OrderEntity order) {
        return new OrderResponse(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getCreatedAt(),
            order.getUpdatedAt(),
            order.getItems().stream()
                .map(item -> new OrderItemResponse(item.getId(), item.getProductCode(), item.getQuantity(), item.getUnitPrice()))
                .toList()
        );
    }
}
