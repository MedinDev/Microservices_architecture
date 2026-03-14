package com.microservices.order.service;

import com.microservices.order.dto.OrderItemRequest;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InventoryServiceClient {

    private final Map<String, Boolean> availabilityCache = new ConcurrentHashMap<>();

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "inventoryFallback")
    @Retry(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    public boolean reserveInventory(List<OrderItemRequest> items) {
        String cacheKey = key(items);
        boolean available = items.stream().noneMatch(item -> item.quantity() > 100);
        if (items.stream().anyMatch(item -> item.productCode().toLowerCase().contains("inventory-down"))) {
            throw new IllegalStateException("Inventory service unavailable");
        }
        availabilityCache.put(cacheKey, available);
        return available;
    }

    public boolean inventoryFallback(List<OrderItemRequest> items, Throwable throwable) {
        return availabilityCache.getOrDefault(key(items), true);
    }

    public void releaseInventory(Long orderId) {
        availabilityCache.remove(String.valueOf(orderId));
    }

    private String key(List<OrderItemRequest> items) {
        return Integer.toString(items.hashCode());
    }
}
