package com.microservices.order.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microservices.order.dto.OrderItemRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryServiceChaosTest {

    private final InventoryServiceClient inventoryServiceClient = new InventoryServiceClient();

    @Test
    void reserveInventoryDetectsServiceFailurePattern() {
        List<OrderItemRequest> items = List.of(new OrderItemRequest("inventory-down-sku", 1, BigDecimal.ONE));

        boolean fallback = inventoryServiceClient.inventoryFallback(items, new RuntimeException("service-down"));

        assertTrue(fallback);
    }

    @Test
    void reserveInventoryHandlesUnavailableCapacity() {
        List<OrderItemRequest> items = List.of(new OrderItemRequest("SKU-HIGH", 150, BigDecimal.ONE));

        boolean reserved = inventoryServiceClient.reserveInventory(items);

        assertFalse(reserved);
    }
}
