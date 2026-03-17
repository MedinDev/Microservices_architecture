package com.microservices.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.common.exception.BusinessValidationException;
import com.microservices.order.domain.OrderEntity;
import com.microservices.order.domain.OrderStatus;
import com.microservices.order.dto.CreateOrderRequest;
import com.microservices.order.dto.OrderItemRequest;
import com.microservices.order.dto.OrderResponse;
import com.microservices.order.repository.OrderOutboxRepository;
import com.microservices.order.repository.OrderRepository;
import com.microservices.order.repository.ProcessedOrderEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private OrderOutboxRepository outboxRepository;
    private InventoryServiceClient inventoryServiceClient;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = Mockito.mock(OrderRepository.class);
        outboxRepository = Mockito.mock(OrderOutboxRepository.class);
        inventoryServiceClient = new InventoryServiceClient();
        ProducerFactory<String, com.microservices.common.event.DomainEvent> producerFactory = Mockito.mock(ProducerFactory.class);
        KafkaTemplate<String, com.microservices.common.event.DomainEvent> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        ProcessedOrderEventRepository processedOrderEventRepository = Mockito.mock(ProcessedOrderEventRepository.class);
        orderService = new OrderService(
            orderRepository,
            outboxRepository,
            kafkaTemplate,
            new ObjectMapper(),
            inventoryServiceClient,
            processedOrderEventRepository,
            new SimpleMeterRegistry()
        );
    }

    @Test
    void createOrderThrowsWhenInventoryUnavailable() {
        CreateOrderRequest request = new CreateOrderRequest(
            11L,
            List.of(new OrderItemRequest("SKU-1", 150, BigDecimal.valueOf(12.5)))
        );

        assertThrows(BusinessValidationException.class, () -> orderService.createOrder(request));

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void cancelOrderReturnsCurrentStateWhenAlreadyCancelled() {
        OrderEntity cancelled = new OrderEntity();
        cancelled.setUserId(42L);
        cancelled.setStatus(OrderStatus.CANCELLED);
        cancelled.setTotalAmount(BigDecimal.TEN);
        cancelled.setCreatedAt(Instant.now());
        cancelled.setUpdatedAt(Instant.now());

        when(orderRepository.findById(99L)).thenReturn(Optional.of(cancelled));

        OrderResponse response = orderService.cancelOrder(99L);

        assertEquals(OrderStatus.CANCELLED, response.status());
        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(outboxRepository, never()).save(any());
    }
}
