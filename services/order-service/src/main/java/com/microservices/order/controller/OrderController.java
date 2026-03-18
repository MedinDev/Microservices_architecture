package com.microservices.order.controller;

import com.microservices.common.audit.AuditableAction;
import com.microservices.common.security.RequiredRole;
import com.microservices.order.domain.OrderStatus;
import com.microservices.order.dto.CreateOrderRequest;
import com.microservices.order.dto.OrderResponse;
import com.microservices.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @RequiredRole("ORDER_USER")
    @AuditableAction("ORDER_CREATE")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(orderService.createOrder(request));
    }

    @GetMapping("/{id}")
    @RequiredRole("ORDER_USER")
    @AuditableAction("ORDER_READ")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping("/user/{userId}")
    @RequiredRole("ORDER_USER")
    @AuditableAction("ORDER_LIST_BY_USER")
    public ResponseEntity<List<OrderResponse>> getUserOrders(@PathVariable Long userId) {
        return ResponseEntity.ok(orderService.getOrdersByUserId(userId));
    }

    @PutMapping("/{id}/cancel")
    @RequiredRole("ORDER_ADMIN")
    @AuditableAction("ORDER_CANCEL")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    @GetMapping("/status/{orderId}")
    @RequiredRole("ORDER_USER")
    @AuditableAction("ORDER_STATUS_TRACK")
    public ResponseEntity<OrderStatus> trackStatus(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderStatus(orderId));
    }
}
